# B-EMAIL-OUTBOX · 새 앱 알림과 이메일 발송 대기 원자 저장

- 담당 B / 검토 A / 기준 main b81139f2 / K-41 0.4 1~6장 AGREED / 2026-11-19.
- 목표: 앱 알림 INSERT가 새 행을 넣은 경우에만, 현재 허용된 수신자의 두 요청 종류를 V39 대기에 같은 트랜잭션으로 저장한다.
- 허용: B NotificationService·EmailOutboxQueue·EmailOutboxRepository 및 대응 단위/DB 검사·작업/인수인계. A의 MailNotificationProperties를 읽기만 사용한다.
- 금지: SQL/번호·A/C 코드·설정/의존성·운영 자료·실제 SMTP/모델 호출. worker/재시도/SMTP 어댑터는 별도 후속이다.
- 완료 기준: 기본 비활성/빈 목록0, 새 INSERT만 대기, 재처리 중복0·새 요청 재예약, 본인/관찰자/탈퇴자 제외, 기존 앱 알림 보존, 실패 시 업무/앱/대기 롤백을 전용 DB로 확인한다.
- 독립된 저장 검토 단위다. 초기 Draft 제출 뒤 최신 main·전체 CI를 확인하면 Ready로 A 검토를 요청한다. 병합은 작성자 외 팀원이 검토 후 진행한다. 실제 발송 활성화는 후속 worker·SMTP 결합 검증 뒤 별도로 판단한다.
- 관련56건(새 DB5·생성 성공 분기 단위2·기존49) 통과. 기본 꺼짐의 기존 알림 DB slice 두 곳은 새 예약 협력자를 mock으로 추가하고, 실제 예약·롤백은 새 전체 컨텍스트 DB 검사에서 확인한다.
