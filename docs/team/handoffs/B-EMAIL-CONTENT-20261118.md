# B · 앱·메일 공통 알림의 실제 조회 응답 연결

- 담당 B / 검토 A / C 소비 #202 / 구현 PR #198.
- K-41 0.4 1~6장은 #197에서 A·C 승인 후 병합됐다. 계약 문서에 남은 승인 대기 표기는 실제 합의 상태로만 정정했다. 7~12장은 후속 DRAFT를 유지한다.
- 최초 응답 검사 기준: main `470348a2` + #198 공통 생성기 + 이번 DTO/서비스 연결. SMTP·V39 선행 병합 뒤 main `465adeee`에서 새 검토 요청 검사까지 추가해 최종49건을 검증했다.

## 변경

기존 NotificationDtos.Item에 message, target, path를 추가하고 NotificationService.list가 공통 생성 결과를 담는다. 기존 필드·정렬·커서·수신자 격리·읽음·targetAvailable 및 발행 코드는 바꾸지 않았다. label은 계약의 추가 응답이 아니므로 내부 메일 제목 구성용으로만 사용한다.

target은 kind와 taskId/deliverableId/reviewId/messageId/commentId/fileId를 포함하며 해당하지 않는 값은 null이다. TASK는 board?task= 경로와 메시지/파일 위치를 보존한다. DELIVERABLE_REVIEW는 ?tab=review 경로에 댓글이 있으면 #comment-{id}를 한 번 붙인다. 대상이 없거나 타 프로젝트에 있으면 기존 targetAvailable=false가 유지되므로 소비자는 이동을 막는다.

SQL·의존성·설정·SMTP·수신 설정·프론트 구현은 변경하지 않았다. 신규 REVIEW_REQUESTED의 운영 발행 이벤트는 활성화하지 않았다. A의 V39가 반영된 뒤 합성 검사의 공통 발행 메서드에서 이 종류를 실제 저장하고 JSON 응답까지 확인했다.

## 검증

Java25.0.3 / Gradle9.8.2 / PostgreSQL16. 최초 전용 DB bb_b_notification_presentation_20261118에서45/45(1분29초)를 검증했다. SMTP·V39 병합 뒤 새 전용 DB bb_b_notification_v39_20261118에서 아래 **49/49**, 실패·오류·건너뜀0, Gradle 실행2분1초다.

| 범위 | 검사 | 결과 |
| --- | --- | --- |
| PostgreSQL + 인증 필터 + MockMvc JSON 응답 | NotificationPresentationHttpTest | 6/6 |
| 실제 DB | NotificationDatabaseTest | 8/8 |
| 실제 DB | NotificationAssignmentDatabaseTest | 8/8 |
| 단위 | NotificationServiceTest | 8/8 |
| 단위 | NotificationPresentationFactoryTest | 8/8 |
| 단위 | EmailNotificationContentTest | 8/8 |
| Spring 컨텍스트·SMTP 설정(실제 발송 없음) | MailSettingsTest | 3/3 |

새 HTTP 계층 검사는 실제 저장된 알림의 문장·대상·경로 및 null 직렬화, 기존 필드 보존, 의견/파일 위치, 검토 댓글 anchor, 없는 대상·타 프로젝트 대상의 이동 불가, 본인 알림만 조회·읽음 가능, 비로그인401·외부인403·타인 읽음404, 페이지 경계·unreadOnly·반복 읽음 시각 유지까지 확인한다. 테스트 자료는 테스트 트랜잭션으로 롤백하며 기존 시연·운영 DB/업로드는 쓰지 않았다.

추가한6번째 HTTP 검사는 V39의 REVIEW_REQUESTED를 실제 저장해 공통 문장·DELIVERABLE_REVIEW 대상·검토 탭 경로·null 댓글/파일 위치를 확인한다. SMTP 설정3건은 A의 기존 검사를 회귀 실행한 것으로 B가 새로 작성한 검사가 아니며 실제 메일을 보내지 않는다.

처음은 MockMvc import 누락으로 컴파일 실패했다. 이를 수정한 첫 DB 실행에서는 합성 완료 요청에 요청자 필드가 빠져 task_request_pair 제약으로 새 검사1건이 실패했다. 요청자·진행 상태를 실제 요청 자료와 맞춘 후 최초45건 전체를 다시 실행해 통과했다. 이후 추가한 V39 포함49건도 통과했다. 실패 조건·DB 제약은 완화하지 않았다.

## 미검증·다음 작업

- MockMvc는 실제 포트를 열어 네트워크로 호출한 검사가 아니다. C가 이 응답을 쓰는 #202의 브라우저 재연동을 수행한다.
- 로컬 전체 backend·실제 SMTP·Gmail 받은편지함·운영 배포는 수행하지 않았다. 전체 backend/DB/frontend는 PR 최종 head의 CI와 구분해 기록한다.
- A: 공통 생성기와 추가 응답을 재검토한다. A SMTP/V39 및 검토 요청 이벤트는 선행 PR로 별도 제공한다.
- B: 발송 대기 저장·worker·재시도는 A의 실제 스키마와 SMTP 기반 병합 뒤 별도 PR에서 구현한다. 공통 응답 PR을 해당 후속의 대기로 묶지 않는다.
- C: label 없이 message/target/path로 소비하고 서버 path의 댓글 anchor를 중복 추가하지 않는다. #198 뒤 #202에서 실제 응답으로 검증한다.

## 같은 날 선행 PR 검토 후 갱신

- A의 #207(V39 발송 대기 표·REVIEW_REQUESTED 제약)과 #206(SMTP 설정·라이브러리)을 B가 소비자 관점으로 검토해 병합했다. B outbox/worker의 실제 구현은 아직 없고 SMTP 어댑터도 후속이다.
- 최신 서버 + #206/#207/#208/#209 검토 결합본을 별도 DB bb_b_mail_foundation_review_20261118에서 실행했다. 총114건 중111건 통과·실제 모델3건 건너뜀, 실패/오류0. MailSettingsTest3, MainMigrationCompatibilityTest3, ReviewRoundServiceTest13, FeedbackLoopHttpTest4와 기존 agent 검사가 포함된다. 이 결과를 로컬 전체 backend·실제 Gmail/LLM 호출로 보고하지 않는다.
- #208의 직접 NotificationService.reviewEvent 호출과 B 소유 FeedbackLoopHttpTest 기대값 변경에 동의했다. 새 검토 요청과 새 버전 알림은 목적이 달라 함께 유지하며, #207→#198→#202 뒤 활성화한다.
- #209는 완료 합성 자료의 담당자 본인 승인, #212는 두 번째 S3 업로드 실패 시 미완성 백업 세트 식별 문제를 별도 수정 요청했다. 관련 결합 검사가 통과해도 이 검토 지적이 해소된 것은 아니다.
