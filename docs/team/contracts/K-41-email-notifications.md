# K-41 · Gmail SMTP 이메일 알림

- 버전 0.1 / 상태 DRAFT / 2026-11-16 / B 생산자, A SMTP 기반·DB·검토 회차 생산자, C 소비자
- 기준 main `035be3f2`. 합의 전 HTTP/DB/운영 동작을 변경하지 않는다. 사용자 선택은 Gmail SMTP이며 수신자/신규 이벤트/발송 이력 계약의 합의는 별도다.

## 1. 최소 범위

기존 앱 알림은 유지한다. 첫 발송 대상은 업무 결과 확인 요청 `TASK_COMPLETION_REQUESTED`, 최종 제출물 새 회차 검토 요청 `REVIEW_REQUESTED`(제안) 두 가지다. 댓글마다 메일을 보내거나 파일을 첨부하지 않는다. 기존 `REVIEW_VERSION_POSTED`는 이전 피드백 작성자에게 수정본을 알리는 것으로 새 검토 요청과 다르므로 대체 사용하지 않는다.

메일은 서비스 전용 Gmail에서 각 수신자에게 개별 발송한다. 수신자 목록을 To/CC로 공유하지 않는다. 본문은 요청한 사람·대상 제목·로그인 후 열 수 있는 원문 링크만 담고 비밀번호/토큰/댓글 원문/파일 내용은 담지 않는다. 제목의 CR/LF는 공백으로 바꾸고 본문은 plain text UTF-8로 시작한다.

## 2. 수신자와 초기 동의 범위

- 업무 요청: 기존 완료 확인의 canReview와 동일한 사람. `NotificationService.completionRequested` 후보·본인/관찰자 제외를 재사용한다.
- 검토 요청: A의 최신 회차 결정 권한을 가진 현재 팀장·팀원에서 행위자 및 파일 업로더를 제외한다. A가 후보를 생산하며 B가 권한을 별도로 복제하지 않는다. 새 회차 id로 재요청을 구분한다.
- 첫 시연은 서버의 명시적 수신 허용 목록(MAIL_RECIPIENT_ALLOWLIST)에 등록하고 수신에 동의한 테스트 계정만 대상으로 한다. 빈 목록은 발송0이다. 가입에 입력된 모든 주소로 자동 발송하지 않는다.
- 발송 직전에도 현재 프로젝트 기여자·본인 제외·허용 목록·유효한 주소·대상 존재 및 요청 대기 상태를 확인한다. 탈퇴/관찰자 전환/삭제/이미 처리된 요청은 SKIPPED로 끝낸다.
- 일반 사용자 수신 설정/주소 확인 API·화면은 이번 초기 SMTP 연결에 자동 포함하지 않는다. 공개 사용으로 확대하려면 C와 별도 계약으로 기본 꺼짐·주소 확인·해제를 제공한다.

## 3. SMTP 기반(A 요청)

- Spring Boot이 관리하는 `spring-boot-starter-mail`을 쓰고 버전을 따로 지정하지 않는다. JavaMailSender, Gmail smtp.gmail.com:587, 인증 및 STARTTLS 필수. TLS 검증을 끄지 않는다.
- MAIL_USERNAME/MAIL_PASSWORD는 발급 계정과 앱 비밀번호로 서버에서만 읽는다. MAIL_ENABLED 기본 false. 비밀번호가 있는 것만으로 켜지지 않는다.
- MAIL_RECIPIENT_ALLOWLIST 기본 빈 값. FRONTEND_BASE_URL은 기존 설정을 재사용하며, 메일의 링크는 그 고정 출처와 UUID 경로만 사용한다. 공개 주소는 https, 개발은 localhost/loopback http만 허용한다. 사용자 입력으로 링크 출처를 고르지 않는다.
- 연결/읽기/쓰기 제한을 명시(예: 각각5초). 사용자/메일/예외의 문자열 전체를 로그에 남기지 않고 대기 id와 분류된 오류만 남긴다.
- .env.example은 비어 있는 예시만, Compose는 필요한 변수만 backend에 전달한다. 프론트에 노출하지 않는다. 로컬 실행은 현재 스크립트가 AI 변수만 읽으므로 MAIL_* 전달을 추가하고, 일반 수신자 발송은 기본 꺼짐을 유지한다. 소스/CI는 teamplay-ui의 실제 .env를 복사하지 않는다.

## 4. 저장과 재시도(B 엔티티/저장소, SQL 번호 A)

업무/검토 변경과 앱 알림 및 발송 대기 기록은 같은 DB 트랜잭션에 저장한다. SMTP는 커밋 후 별도 작업으로 실행한다. 단순 메모리 afterCommit만으로 발송을 맡기지 않고 재시작해도 남는 대기 기록을 둔다. SMTP 실패는 이미 커밋된 업무와 앱 알림을 롤백시키지 않는다.

새 테이블 초안(A가 번호·DDL 확정): id UUID PK, project_id/recipient_id/actor_id UUID, event_type varchar(40), event_key varchar(200), task_id/deliverable_id/review_id nullable UUID, target_title varchar(255), actor_name varchar(100), status(PENDING/PROCESSING/SENT/FAILED/SKIPPED), attempt_count, available_at, claimed_at, sent_at, error_code(분류값), created_at/updated_at. UNIQUE(event_key,recipient_id). 기존 migration과 user_notifications 응답은 수정하지 않는다. 수신 주소는 평문 이력으로 중복 저장하지 않고 발송 시 사용자에서 읽는다.

작업 획득은 짧은 트랜잭션의 원자적 claim/lease로 한 worker만 처리하며 네트워크를 기다리는 동안 업무 트랜잭션/행 잠금을 잡지 않는다. 연결 오류/일시 제한은 제한된 지수 지연으로 재시도(최대3회). 인증/설정 오류는 반복하지 않고 운영 조치 대상으로 표시한다. 처리 중 서버가 내려가면 lease 만료 후 다시 처리한다.

SMTP 수락 뒤 SENT 저장 전에 프로세스가 종료되면 중복 수신할 수 있다. 가능한 동일 Message-ID를 재사용하되 수신 서버의 중복 제거를 보장하지 않는다. '정확히 한 번 전달'을 약속하지 않는다.

## 5. B가 독립 진행할 수 있는 내부 연결

B는 발송 정책/메일 내용 생성기 및 `EmailNotificationTransport` 내부 경계를 라이브러리 추가 없이 작성·단위 검증한다. A의 SMTP 어댑터는 이 경계에 연결한다. DB·사용자 API는 계약 합의와 SQL 병합 뒤 별도 B 구현 PR이다. 계약 색인과 공통 설정은 A가 반영한다.

## 6. 합의/검증 요청

- A: starter-mail·MAIL_* 전달·TLS/시간 제한·새 SQL 번호·ReviewRoundService의 신규 검토 요청 후보/이벤트. 직접 작성 또는 임시 작성 경계 확인.
- C: 기존 board?task= 및 제출물 검토 탭 링크 확인. 초기 수신 허용 목록으로 범위를 제한하고 일반 수신 설정은 후속으로 두는 것 확인.
- B 검사: 빈 설정/허용 목록, 수신자 경계, 제목 줄바꿈/긴 한글·이모지, 원문 링크, 커밋/롤백, 중복/claim 경합, SMTP 실패와 재시작 대기 복구. mock/실제 DB/로컬 SMTP/Gmail 수신을 각각 구별한다.
- 공식 자료: https://docs.spring.io/spring-boot/reference/io/email.html / https://support.google.com/accounts/answer/185833 / https://developers.google.com/workspace/gmail/imap/imap-smtp
