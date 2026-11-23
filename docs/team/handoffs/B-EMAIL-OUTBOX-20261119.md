# B · 앱 알림과 이메일 발송 대기 원자 저장

- 기준 main b81139f2 / 브랜치 codex/B-email-outbox / K-41 0.4 1~6장 AGREED.
- 선행 #198(공통 알림·정책)·#206(MailNotificationProperties)·#207(V39)는 병합돼 있다. SQL/기존 migration/설정/의존성/화면은 변경하지 않는다.

## 구현

NotificationService의 두 INSERT 결과가1행인 경우에만 EmailOutboxQueue를 호출한다. 중복 앱 사건의0행에는 예약을 하지 않으며 켜짐/허용 목록 변경 후 기존 앱 알림을 소급 예약하지 않는다. 다른 알림 발행·수신자·읽음/페이지 API는 유지한다.

EmailOutboxQueue는 TASK_COMPLETION_REQUESTED와 REVIEW_REQUESTED만 받는다. MailNotificationProperties의 기본 비활성·빈 목록에서는 저장하지 않으며, 기존 EmailNotificationPolicy로 같은 프로젝트의 현재 기여자·허용 주소·수신자 일치·행위자 제외를 확인한다. 담당/검토 후보의 업무별 자격은 기존 사건 생산자가 판정한다. 업무/제출물 대상 스냅숏과 같은 event_key·수신자로 V39에 저장하고 수신 주소·본문·파일 내용은 저장하지 않는다.

EmailOutboxRepository는 JdbcTemplate으로 V39에 INSERT … ON CONFLICT DO NOTHING을 실행한다. Queue와 저장소 모두 MANDATORY라 caller의 업무/앱 알림 트랜잭션에 참여한다. 별도 트랜잭션을 만들거나 SMTP를 호출하지 않는다. 상태 PENDING·시도0은 A DDL의 기본값이며, available_at/created_at/updated_at에는 앱 알림 생성 시각을 넣는다. 후속 updated_at은 기존 DB 트리거가 관리한다.

## 검증

Java25.0.3 / Gradle9.8.2 / PostgreSQL16 전용 DB bb_b_email_outbox_20261119. 관련 **56/56**, 실패·오류·건너뜀0, 최종 실행2분17초.

| 범위 | 검사 | 건수 |
| --- | --- | --- |
| 새 실제 DB | EmailOutboxQueueDatabaseTest | 5 |
| 기존 앱·배정 실제 DB | NotificationDatabaseTest / NotificationAssignmentDatabaseTest | 16 |
| PostgreSQL + 인증 MockMvc 응답 | NotificationPresentationHttpTest | 6 |
| 단위(기존8+새 INSERT 성공 분기2) | NotificationServiceTest | 10 |
| 단위 | NotificationPresentationFactoryTest / EmailNotificationContentTest | 16 |
| Spring SMTP 설정, 발송 없음 | MailSettingsTest | 3 |

새 DB 검사: 같은 요청 재처리1건·다른 요청 재예약, 새 검토 요청만 대기(파일/새 버전 제외), 허용 목록 밖 팀원의 앱 알림 유지, 비활성/빈 목록·다시 켬의 소급 금지, 본인·관찰자·타 프로젝트·탈퇴 멤버 제외, 실제 앱/대기 INSERT 후 강제 실패 시 업무·앱·대기0건 롤백을 확인한다. 새 Queue 검사의 합성 자료는 롤백된다. 기존 배정 DB 검사는 전용 DB에 커밋 자료를 남기는 기존 방식을 유지한다.

초기56건에서 기존 DB slice의 새 협력자 누락16건, MANDATORY 저장소 spy를 트랜잭션 밖에서 설정한 실패와 미완성 stub의 후속 영향이 있었다. slice에 예약 mock을 넣고 실패 주입 설정도 실제 트랜잭션 안으로 옮겼다. 그다음55/56에서는 주입한 IllegalStateException이 저장소 예외 변환으로 감싸지는 검사 기대값 문제가 남았다. 정확한 전용 합성 실패 타입을 사용해 실제 INSERT/flush 확인과 롤백 조건을 유지한 뒤 전체56건을 재실행해 통과했다. 운영 트랜잭션 규칙이나 검사를 완화하지 않았다.

## 미검증·다음

- 로컬 전체 backend·worker 경합/lease·SMTP/실제 Gmail·브라우저·운영 적용은 수행하지 않았다. 최종 main 반영 뒤 PR 전체 CI를 별도로 기록한다.
- worker·SMTP 어댑터·재시도/최대3회·발송 직전 대상/수신 조건 재확인은 후속이다. 초기 Draft였으며 11/23 최신 main·전체 CI 확인 뒤 저장 단위의 Ready 검토를 요청한다. 실제 발송은 이 PR과 별개로 검증하고 활성화한다.
- A에게 기존 EmailNotificationTransport의 다섯 결과와 deliveryId/Message-ID를 따르는 SMTP 어댑터를 별도 PR로 요청했다. 운영 MAIL_ENABLED를 활성화하지 않는다.
- A는 JDBC/JPA 트랜잭션 결합, 새 INSERT 조건과 허용 정책을 검토한다. 후속 발송 경로 준비와 검토 전 B가 직접 병합하지 않는다.
