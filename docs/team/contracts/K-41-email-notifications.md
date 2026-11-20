# K-41 · Gmail SMTP 이메일 알림

- 버전 0.4 / 2026-11-16 / B 알림·수신 설정 생산자, A SMTP·인증·DB·검토 회차 생산자, C 화면 소비자
- 상태: **1~6장 AGREED(#197 A·C 승인 및 병합), 7~12장 후속 DRAFT**. 후속의 이번 학기 구현 여부·착수 시점은 주간 계획에서 별도 합의한다. 후속 미구현을 초기 SMTP 구현의 대기로 묶지 않는다.
- 기준 main `035be3f2`. Gmail SMTP를 공급자로 사용하며 수신자·신규 이벤트·발송 이력을 제안한다. 합의 전 HTTP/DB/운영 동작을 변경하지 않는다.

## 1. 최소 범위

기존 앱 알림은 유지한다. 첫 발송 대상은 업무 결과 확인 요청 `TASK_COMPLETION_REQUESTED`, 최종 제출물 새 회차 검토 요청 `REVIEW_REQUESTED` 두 가지다. 검토 요청은 앱 알림에도 저장해 메일이 실패해도 앱에서 확인할 수 있게 한다. 댓글마다 메일을 보내거나 파일을 첨부하지 않는다. 기존 `REVIEW_VERSION_POSTED`는 이전 피드백 작성자에게 수정본을 알리는 것으로 새 검토 요청과 다르므로 대체 사용하지 않는다.

메일은 서비스 전용 Gmail에서 각 수신자에게 개별 발송한다. 수신자 목록을 To/CC로 공유하지 않는다. 본문은 요청한 사람·대상 제목·로그인 후 열 수 있는 원문 링크만 담고 비밀번호/토큰/댓글 원문/파일 내용은 담지 않는다. 제목의 CR/LF는 공백으로 바꾸고 본문은 plain text UTF-8로 시작한다.

## 2. 수신자와 초기 동의 범위

- 업무 요청: 기존 완료 확인의 canReview와 동일한 사람. `NotificationService.completionRequested` 후보·본인/관찰자 제외를 재사용한다.
- 검토 요청: A의 최신 회차 결정 권한을 가진 현재 팀장·팀원에서 행위자 및 파일 업로더를 제외한다. A가 후보를 생산하며 B가 권한을 별도로 복제하지 않는다. 새 회차 id로 재요청을 구분한다.
- 첫 시연은 서버의 명시적 수신 허용 목록(MAIL_RECIPIENT_ALLOWLIST)에 등록하고 수신에 동의한 테스트 계정만 대상으로 한다. 빈 목록은 발송 0건이다. 가입에 입력된 모든 주소로 자동 발송하지 않는다.
- 발송 직전에도 현재 프로젝트 기여자·본인 제외·허용 목록·유효한 주소·대상 존재 및 요청 대기 상태를 확인한다. 탈퇴/관찰자 전환/삭제/이미 처리된 요청은 발송하지 않는다.
- 일반 사용자 수신 선택·주소 확인은 7~12장의 후속 범위다. 초기 SMTP 연결과 구별하고 A/C 합의 뒤 별도 구현 PR로 진행한다.

## 3. SMTP 기반(A 요청)

- Spring Boot이 관리하는 `spring-boot-starter-mail`을 쓰고 버전을 따로 지정하지 않는다. JavaMailSender, Gmail smtp.gmail.com:587, 인증 및 STARTTLS 필수. TLS 검증을 끄지 않는다.
- MAIL_USERNAME/MAIL_PASSWORD는 발급 계정과 앱 비밀번호로 서버에서만 읽는다. MAIL_ENABLED 기본 false. 비밀번호가 있는 것만으로 켜지지 않는다.
- MAIL_ENABLED=false면 메일 자격 증명이 없어도 PMS가 기동하며 시작/health 검사에서 SMTP에 접속하지 않는다.
- MAIL_RECIPIENT_ALLOWLIST 기본 빈 값. FRONTEND_BASE_URL은 기존 설정을 재사용하며, 메일의 링크는 그 고정 출처와 UUID 경로만 사용한다. 공개 주소는 https, 개발은 localhost/loopback http만 허용한다. 사용자 입력으로 링크 출처를 고르지 않는다.
- 연결/읽기/쓰기 제한을 명시(예: 각각5초). 사용자/메일/예외의 문자열 전체를 로그에 남기지 않고 대기 id와 분류된 오류만 남긴다.
- .env.example은 비어 있는 예시만, Compose는 필요한 변수만 backend에 전달한다. 프론트에 노출하지 않는다. 로컬 실행은 현재 스크립트가 AI 변수만 읽으므로 MAIL_* 전달을 추가하고, 일반 수신자 발송은 기본 꺼짐을 유지한다. 소스/CI는 teamplay-ui의 실제 .env를 복사하지 않는다.

## 4. 발송 대기 기록과 재시도

업무/검토 변경·앱 알림·메일 발송 대기 기록을 같은 DB 트랜잭션에 저장한다. 새 앱 알림 INSERT가 성공한 경우에만 발송 대기를 만든다. 기존 (event_key, recipient_id) 고유 제약에 걸린 중복 이벤트에는 대기를 다시 만들지 않는다. A의 새 회차 이벤트는 원래 트랜잭션 안에서 B 알림 발행 경계로 연결한다. SMTP는 업무 트랜잭션에서 호출하지 않는다.

테이블 제안 이름은 `notification_email_outbox`다. id UUID PK, project_id/recipient_id/actor_id UUID, event_type varchar(40), event_key varchar(200), task_id/deliverable_id/review_id nullable UUID, target_title varchar(255), actor_name varchar(100), status(PENDING/PROCESSING/SENT/FAILED/SKIPPED), attempt_count, available_at, claimed_at, sent_at, error_code(분류 코드), created_at/updated_at을 둔다. UNIQUE(event_key,recipient_id), 대기 시각 인덱스를 둔다. 시각은 timestamptz, 초기 attempt_count=0이다. A가 DDL과 실제 타입/인덱스를 확정하며 B가 임의 SQL을 작성하지 않는다.

worker는 커밋된 대기를 짧은 별도 트랜잭션의 `FOR UPDATE SKIP LOCKED`로 획득하고 claimed_at/attempt_count를 원자적으로 갱신한다. SMTP 동안 DB 행 잠금을 유지하지 않는다. 발송 직전 현재 수신 조건을 읽고, 결과 저장은 id와 획득한 claimed_at을 함께 대조해 만료된 worker가 새 획득 결과를 덮지 않게 한다. 프로세스 종료로 남은 PROCESSING은 lease 만료 후 회수한다. 전송 시간 제한보다 긴 lease(제안 2분)를 쓰고 큐/배치 크기 상한을 둔다.

일시 실패는 최대 3회 시도(첫 시도 포함)하고 지연을 두며, 인증/영구 실패는 반복하지 않는다. 마지막 실패는 FAILED로, 수신 조건이 사라진 건은 SKIPPED로 끝낸다. 재시작 후 커밋된 PENDING과 만료된 PROCESSING을 다시 읽는다. SMTP 수락 뒤 SENT 기록 전 중단되면 중복 수신할 수 있으므로 같은 delivery id의 Message-ID를 재사용하되 정확히 한 번 배달을 보장하지 않는다. 원본 SMTP 예외/주소/본문은 로그에 넣지 않고 대기 id와 분류 코드만 기록한다. 메일 실패는 이미 커밋된 업무·앱 알림을 되돌리지 않는다.

A의 최신 #197 리뷰에서 발송 대기 표의 **V39 예약**을 확인했다. 동일한 새 migration에서 REVIEW_REQUESTED 앱 알림 타입 제약 확장도 확인을 요청한다. 현 V32의 user_notifications_type_check 열거에 새 타입은 없다. V39의 실제 작성·번호 최종 확인은 A가 맡고 기존 SQL/체크섬은 수정하지 않는다. 수신 설정·주소 확인 후속 스키마는 A가 별도 배정한다.

## 5. 앱·메일 공통 문장과 대상(B 생산, C 소비)

B의 생성기 하나가 기존 알림 스냅숏으로 message·target·path를 만들고 앱 알림 응답과 메일 본문이 같은 결과를 쓴다. 문장은 조회/메일 생성 시 만들며 옛 알림도 현재 문구로 표시한다. 기존 DB의 문자열 스냅숏 자체를 덮어쓰지 않는다. Discord 문장은 이번 범위에서 변경하지 않는다.

기존 GET 알림 응답 `NotificationDtos.Item`에 아래 세 필드만 추가하고 기존 필드·페이지·읽음/권한·targetAvailable 의미는 유지한다.

| 필드 | 의미 |
| --- | --- |
| message | 서버가 만든 표시 문장. 예: `김민지님이 ‘자료 정리’ 업무의 결과 확인을 요청했습니다.` |
| target | kind(TASK/DELIVERABLE_REVIEW/UNKNOWN)와 taskId, deliverableId, reviewId, messageId, commentId, fileId. 해당 없는 id는 null |
| path | 서버가 만든 같은 사이트의 `/projects/{projectId}/...` 상대 경로. 유효한 대상 경로를 만들 수 없으면 null |

TASK는 기존 `/projects/{projectId}/board?task={taskId}`, DELIVERABLE_REVIEW는 `/projects/{projectId}/deliverables/{deliverableId}?tab=review`를 쓴다. 피드백 위치가 있으면 기존 `#comment-{commentId}`를 유지한다. source id는 같은 프로젝트의 대상인지 확인한다. targetAvailable=false면 화면은 이동을 막고 메일은 보내지 않는다. 모르는 type은 일반 문장을 쓰되 유효한 대상 id의 종류/경로를 보존하고, 대상도 모르면 UNKNOWN/path=null로 표시만 한다.

C는 message를 그대로 표시하고 TASK는 그 자리에서 업무 창을 열되 target.messageId/fileId의 기존 위치 이동을 유지한다. 다른/모르는 kind는 유효한 같은 프로젝트의 path가 있으면 그 경로로 이동한다. message/target/path는 필수 응답이다. 화면의 notificationLabels와 기존 칸 존재 여부로 대상을 다시 정하는 코드는 제거하고 옛 서버용 문구/대상 fallback은 두지 않는다. 필드 누락/손상 응답은 이동하지 않고 갱신/오류 안내로 처리한다. 서버·화면은 같은 릴리스 태그로 함께 전환한다. 기존 검토 탭/댓글 이동 동작은 공통 target/path로 유지하며 로그인 뒤 원문 복귀는 #199를 따른다.

B가 공통 생성기/DTO 추가를 먼저 제공하고 C가 소비 화면을 전환한다. 새 검토 타입 활성화는 A의 제약 확장과 C 소비 확인 뒤 진행한다. #198의 초안 메일 생성기는 이 공통 결과를 사용하도록 후속 갱신한다.

## 6. 합의/검증 요청

- A: SMTP 라이브러리·MAIL_* 전달·TLS/시간 제한·새 회차 이벤트, REVIEW_REQUESTED 타입 제약 확장용 새 SQL 번호. 수신 설정/주소 확인의 후속은 7~12장에서 별도 합의한다.
- C: 공통 message/target/path 필수 응답 소비, 기존 업무 위치 이동/검토 탭 보존, notificationLabels/기존 칸으로 정하는 분기 제거. 수신 설정 후속과 토큰 링크는 7~12장 DRAFT에서 확인한다.
- B 검사: 빈 설정/허용 목록, 수신자 경계, 제목 줄바꿈/긴 한글·이모지, 공통 문장/원문 링크, 업무 롤백·중복 INSERT 시 대기 없음, worker 경합·lease 회수·이전 획득 결과 저장 거절·최대 시도 횟수·SMTP 실패와 앱 알림 보존. mock·실제 DB·로컬 SMTP·Gmail 수신을 구별한다.
- 공식 자료: [Spring 메일 지원](https://docs.spring.io/spring-boot/reference/io/email.html), [Google 앱 비밀번호](https://support.google.com/accounts/answer/185833), [Gmail SMTP](https://developers.google.com/workspace/gmail/imap/imap-smtp).

## 7. 사용자 흐름과 범위

가입할 때 `[선택] 팀프로젝트 이메일 알림 받기`를 기본 해제 상태로 표시한다. 설명은 `업무 결과 확인·최종 제출물 검토 요청을 가입한 이메일로 받습니다. 받으려면 이메일 주소 확인이 필요합니다.`로 제안한다. 선택하지 않아도 회원가입·로그인·프로젝트 생성/참여·업무·앱 알림을 모두 사용할 수 있다.

체크하면 수신 의향만 저장하고 가입은 바로 완료한다. 회원가입 응답은 SMTP나 이메일 확인을 기다리지 않는다. 가입 후 프로필 설정에서 `확인 메일 보내기`를 눌러 본인 주소를 확인한다. 확인 전에도 PMS를 계속 사용할 수 있고, 이메일 알림 설정만 `주소 확인 필요` 상태로 표시한다. 신규 인증을 로그인/프로젝트 권한 조건으로 추가하지 않는다.

기존 회원은 기본 off. 프로필 설정에서 언제든 켜고 끌 수 있다. 수신 주소는 계정에 등록한 현재 주소를 사용하며 별도 임의 주소 입력은 제공하지 않는다. Gmail 외 네이버·다음·Outlook·학교 주소도 같은 흐름을 사용하고 수신자 Google 연동은 요구하지 않는다.

업무 메일 범위는 K-41의 업무 결과 확인·최종 제출물 검토 요청만이다. 광고/홍보 수신 선택은 포함하지 않는다. 개인정보 안내에 계정 관리·팀프로젝트 업무 알림 목적과 실제 메일 처리 방식을 맞추는 작업은 A가 확인한다. 문구 적용은 제품 정책/법적 요건 확인과 구별한다.

## 8. 알림 설정 API(B 제안)

인증된 본인에게만 다음 API를 제공한다. 프로젝트별 설정은 이번 범위에 추가하지 않는다.

| 메서드·경로 | 입력 | 응답 |
| --- | --- | --- |
| GET `/api/notifications/email-settings` | 없음 | 아래 상태, 200 |
| PUT `/api/notifications/email-settings` | `{ "enabled": true }` 또는 false | 저장 뒤 아래 상태, 200 |

응답 예시: `{ "enabled": true, "emailVerified": false, "verificationRequired": true, "updatedAt": "2026-11-16T10:00:00Z" }`. emailVerified는 A가 보장하는 현재 계정 주소의 확인 상태다. verificationRequired는 enabled && !emailVerified. updatedAt은 설정 기록이 없으면 null, 변경 시 UTC 마이크로초다. 같은 값을 다시 저장하면 설정 버전/시각을 불필요하게 올리지 않는다.

enabled는 알림 수신 의향이며 실제 SMTP 수락/배달 완료를 뜻하지 않는다. enabled=true라도 주소 확인 전에는 업무 메일을 예약하지 않는다. 주소 확인을 마치면 그 뒤 발생한 새 이벤트부터 대상이 되며 과거 알림을 한꺼번에 재발송하지 않는다.

요청은 사용자 id/수신 주소/인증 상태를 받지 않고 인증된 사용자로 대상을 정한다. 비로그인401, enabled 누락/null/형식 오류400. 다른 사람의 설정을 읽거나 바꾸는 API는 제공하지 않는다.

## 9. 가입·주소 확인 연결(A 제안)

기존 POST `/api/auth/signup`에 선택 필드 `emailNotificationsEnabled`를 추가한다. 누락/null은 false로 처리하여 옛 클라이언트/테스트 payload를 유지한다. A는 가입 트랜잭션에서 B의 설정 저장 경계를 호출한다. 이 선택은 프로젝트 참여 동의·개인정보 필수 동의·Google Calendar 연결과 별개다.

주소 확인 API 초안(인증된 본인만):

| 메서드·경로 | 입력 | 성공·오류 |
| --- | --- | --- |
| POST `/api/auth/email-verification/request` | 없음, 현재 계정 주소 사용 | 발송 요청202, 이미 확인한 주소204, 재요청 제한429+Retry-After, 메일 기능 사용 불가503 |
| POST `/api/auth/email-verification/confirm` | `{ "token": "..." }` | 현재 주소 확인204, 잘못된 토큰400, 만료410, 주소 변경409 |

확인 메일은 사용자가 설정에서 명시적으로 요청한 주소 확인 목적이다. 업무 메일의 미확인 주소 차단과 구별하여 A가 생산한다. 재전송 간격 최소 60초와 계정별 시간당 상한을 적용하며 한 번의 요청이 여러 건을 만들지 않게 한다. token은 예측 불가·단일 사용·짧은 만료(제안 15분)로 사용자·정규화된 현재 주소·확인 목적에 묶고 DB에는 원문 대신 해시를 저장한다. 다른 계정 토큰과 옛 주소 토큰은 거절한다. 확인 성공으로 로그인 세션이나 다른 권한을 부여하지 않는다.

확인 링크는 `{FRONTEND_BASE_URL}/email/verify#token={token}`으로 고정한다. 토큰은 fragment로만 전달하고 화면은 먼저 sessionStorage의 해당 탭 임시 값으로 옮긴 뒤 history.replaceState로 주소에서 지운다. 그 뒤 로그인이 필요하면 `/login?next=/email/verify`로 이동하며 next·쿼리·서버 접근 로그에 토큰을 싣지 않는다. 로그인 뒤 확인 화면에서 임시 토큰으로 confirm을 POST하고 성공/잘못된 토큰/만료/주소 변경 처리 후 임시 값을 지운다. 토큰을 PR·예외·활동 로그에 남기지 않고, 링크 GET만으로 확인 상태를 바꾸지 않는다. C의 #200 리뷰 제안을 반영한 경로이며 #199의 안전한 로그인 복귀와 연결한다.

계정 이메일 변경이 가능해지면 이전 확인 상태를 반드시 해제한다. 설정 의향은 유지하되 새 주소 확인까지 업무 메일은 중단한다. 이 주소 변경 동작은 A 소유이며 B는 그 상태를 읽는 공개 내부 경계를 합의한다.

## 10. 저장·발송 조건과 철회

B 엔티티 초안: `email_notification_preferences`의 user_id PK, enabled 기본 false, notice_version, enabled_at/disabled_at/updated_at. 선택 문구 버전과 설정 변경 시각을 기록한다. 실제 테이블·제약·번호·기존 사용자 기본값 SQL은 A가 확정한다. A의 확인 상태 제안은 users.email_verified_at(null이면 미확인)과 별도 단일 사용 토큰 표다. B는 현재 주소의 확인 시각을 읽는 경계만 사용하며 중복 인증 상태를 만들지 않는다.

공개 업무 알림은 현재 주소 확인 + 수신 의향 + 기존 수신 권한 + 서비스 발송 설정을 모두 만족해야 한다. 테스트 허용 목록은 초기 검증 범위이며 공개 회원 수신 설정 완료를 뜻하지 않는다. 이 후속 수신 설정을 적용한 뒤 초기 허용 목록을 추가 제한으로 유지할 수 있으나 알림 off/미확인 주소 차단을 우회하는 설정으로 사용하지 않는다.

후속 수신 설정 적용 때 발송 직전 현재 enabled와 주소 확인 상태를 읽는다. 마지막 enabled_at과 현재 주소의 email_verified_at이 모두 대기 생성 시각보다 앞서야 발송한다. 이 조건으로 off→on이나 주소 변경 후 재확인이 과거 대기를 되살리지 않게 하며 대기 표의 별도 설정 버전/주소 식별 컬럼은 추가하지 않는다. 알림 해제·회원 탈퇴·관찰자 전환·요청 처리/취소 등 발송 조건이 사라진 대기는 SKIPPED로 끝낸다. 이미 SMTP 전송을 시작하거나 수락된 메일은 회수할 수 없으며 해제 후 도착 0건을 보장하지 않는다.

## 11. 개발·자동 테스트

- 일반 단위/DB/브라우저 테스트의 가입은 이메일 인증 없이 기존처럼 진행한다. enabled를 생략하거나 false면 SMTP가 필요 없다.
- 알림 테스트는 전용 DB fixture에서 A의 확인된 테스트 사용자와 B의 수신 설정을 구성한다. 사용자 API에 인증 생략용 헤더/쿼리/공용 토큰을 넣지 않는다.
- 인증 흐름은 Mailpit의 격리된 테스트 수신함에서 확인 링크를 읽어 실제 확인 API까지 자동화한다. 컨테이너 이름은 `blackbox_mail_test`, SMTP는 `127.0.0.1:28125`(컨테이너1025), 수신 UI/API는 `http://127.0.0.1:28025`(컨테이너8025)로 제안한다. 외부 노출/전달/릴레이를 켜지 않으며 이미지 태그·다이제스트는 A가 고정한다.
- 테스트 전용 설정은 `MAIL_HOST=127.0.0.1`, `MAIL_PORT=28125`, `MAIL_AUTH_ENABLED=false`, `MAIL_STARTTLS_ENABLED=false`를 제안한다. 별도 test-mail 프로필과 loopback SMTP 조합에서만 이 예외를 허용하고 일반 서버의 Gmail 인증/STARTTLS 필수 조건은 유지한다. A SMTP 기반 PR에서 이름·프로필을 확정한 뒤 C 브라우저 시험이 같은 값을 사용한다. 테스트 발신·수신 주소는 example.test 합성 주소를 쓰고 실제 Gmail 앱 비밀번호를 불러오지 않는다.
- Mailpit 수신 성공은 실제 Gmail 배달 성공과 구별한다. [Mailpit 공식 Docker/기본 포트](https://mailpit.axllent.org/docs/install/docker/), [설정](https://mailpit.axllent.org/docs/configuration/)을 기준으로 한다.
- 경계: 체크 유무와 상관없이 가입/로그인/PMS 가능, 기존 가입 payload, 미확인 주소 업무 메일 0건, 인증 재전송 제한·토큰 재사용/만료/다른 사용자/주소 변경, 본인 설정만 변경, 중복 PUT, off 후 대기 제외, 재활성화 때 과거 메일 없음.

## 12. 합의 요청과 구현 순서

1. A: 가입 필드·확인 상태의 내부 조회 경계·인증 API와 토큰·rate limit·SQL/SMTP 담당 확인.
2. C: 가입 선택 문구·설정 상태/주소 확인 버튼·메일 링크 로그인 복귀 확인. 가입 필수 절차를 늘리지 않는 흐름을 확인한다.
3. B: 합의 후 설정 저장/API를 별도 PR로 구현. A SQL/확인 상태 경계 뒤 실제 DB/HTTP 검증하고 메일 발송 처리에 설정 버전/철회를 연결한다.
4. A/C 소비자 확인 뒤 공개 회원 발송을 켠다. 초기 테스트 발송과 공개 수신 기능 완료를 구별한다.
