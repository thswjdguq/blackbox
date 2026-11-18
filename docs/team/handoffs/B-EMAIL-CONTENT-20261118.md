# B · 앱·메일 공통 알림의 실제 조회 응답 연결

- 담당 B / 검토 A / C 소비 #202 / 구현 PR #198.
- K-41 0.4 1~6장은 #197에서 A·C 승인 후 병합됐다. 계약 문서에 남은 승인 대기 표기는 실제 합의 상태로만 정정했다. 7~12장은 후속 DRAFT를 유지한다.
- 응답 검사 기준: main `470348a2` + #198 공통 생성기 + 이번 DTO/서비스 연결. 이후 main 동기화에서 backend 변경이 없으면 같은 검사 반복 대신 PR 최종 CI를 확인한다.

## 변경

기존 NotificationDtos.Item에 message, target, path를 추가하고 NotificationService.list가 공통 생성 결과를 담는다. 기존 필드·정렬·커서·수신자 격리·읽음·targetAvailable 및 발행 코드는 바꾸지 않았다. label은 계약의 추가 응답이 아니므로 내부 메일 제목 구성용으로만 사용한다.

target은 kind와 taskId/deliverableId/reviewId/messageId/commentId/fileId를 포함하며 해당하지 않는 값은 null이다. TASK는 board?task= 경로와 메시지/파일 위치를 보존한다. DELIVERABLE_REVIEW는 ?tab=review 경로에 댓글이 있으면 #comment-{id}를 한 번 붙인다. 대상이 없거나 타 프로젝트에 있으면 기존 targetAvailable=false가 유지되므로 소비자는 이동을 막는다.

SQL·의존성·설정·SMTP·수신 설정·프론트 구현은 변경하지 않았다. 신규 REVIEW_REQUESTED는 공통 생성기에만 있고 DB 저장 이벤트로 활성화하지 않았다. 현재 DB 검사에는 이미 허용된 알림 종류만 사용했다.

## 검증

Java25.0.3 / Gradle9.8.2 / PostgreSQL16. 새 전용 DB bb_b_notification_presentation_20261118에서 아래 **45/45**, 실패·오류·건너뜀0. 최종 Gradle 실행 1분29초.

| 범위 | 검사 | 결과 |
| --- | --- | --- |
| PostgreSQL + 인증 필터 + MockMvc JSON 응답 | NotificationPresentationHttpTest | 5/5 |
| 실제 DB | NotificationDatabaseTest | 8/8 |
| 실제 DB | NotificationAssignmentDatabaseTest | 8/8 |
| 단위 | NotificationServiceTest | 8/8 |
| 단위 | NotificationPresentationFactoryTest | 8/8 |
| 단위 | EmailNotificationContentTest | 8/8 |

새 HTTP 계층 검사는 실제 저장된 알림의 문장·대상·경로 및 null 직렬화, 기존 필드 보존, 의견/파일 위치, 검토 댓글 anchor, 없는 대상·타 프로젝트 대상의 이동 불가, 본인 알림만 조회·읽음 가능, 비로그인401·외부인403·타인 읽음404, 페이지 경계·unreadOnly·반복 읽음 시각 유지까지 확인한다. 테스트 자료는 테스트 트랜잭션으로 롤백하며 기존 시연·운영 DB/업로드는 쓰지 않았다.

처음은 MockMvc import 누락으로 컴파일 실패했다. 이를 수정한 첫 DB 실행에서는 합성 완료 요청에 요청자 필드가 빠져 task_request_pair 제약으로 새 검사1건이 실패했다. 요청자·진행 상태를 실제 요청 자료와 맞춘 후 위45건 전체를 다시 실행해 통과했다. 실패 조건·DB 제약은 완화하지 않았다.

## 미검증·다음 작업

- MockMvc는 실제 포트를 열어 네트워크로 호출한 검사가 아니다. C가 이 응답을 쓰는 #202의 브라우저 재연동을 수행한다.
- 로컬 전체 backend·실제 SMTP·Gmail 받은편지함·운영 배포는 수행하지 않았다. 전체 backend/DB/frontend는 PR 최종 head의 CI와 구분해 기록한다.
- A: 공통 생성기와 추가 응답을 재검토한다. A SMTP/V39 및 검토 요청 이벤트는 선행 PR로 별도 제공한다.
- B: 발송 대기 저장·worker·재시도는 A의 실제 스키마와 SMTP 기반 병합 뒤 별도 PR에서 구현한다. 공통 응답 PR을 해당 후속의 대기로 묶지 않는다.
- C: label 없이 message/target/path로 소비하고 서버 path의 댓글 anchor를 중복 추가하지 않는다. #198 뒤 #202에서 실제 응답으로 검증한다.
