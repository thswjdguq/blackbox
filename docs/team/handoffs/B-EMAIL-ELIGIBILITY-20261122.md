# B · 업무 확인 요청 이메일의 발송 직전 재확인

- 기준 main `3d033ae9`, K-41 0.4 1~6장 AGREED, 2026-11-22.
- 소유권: 신규 B 서비스·단위/DB 테스트·개인 작업 문서5개만 추가한다. 기존 A/C·업무 서비스·알림 경로·설정·API·마이그레이션은 바꾸지 않는다. #214/#217의 미병합 소스를 가져오지 않는다.

## 동작

TaskRequestEmailEligibility.currentRecipient는 TASK_COMPLETION_REQUESTED의 메타데이터를 받아 발송 가능한 현재 계정 주소 또는 빈 결과를 반환한다. 입력 객체를 저장하거나 SMTP를 부르지 않는다. 설정 꺼짐·빈 목록·지원하지 않는 종류/대상 모양이면 트랜잭션과 저장소 조회를 시작하지 않는다.

별도의 읽기 전용 트랜잭션에서 프로젝트·사용자·현재 멤버와 주소를 다시 읽고 기존 EmailNotificationPolicy를 적용한다. TaskWorkflowService.view.canReview를 사용해 확인 권한을 복제하지 않는다. 진행 중·미승인·요청 중인 업무이면서 요청자와 요청 시각의 event_key까지 같은 경우만 허용한다. 그래서 취소/보완 후 재요청이 있어도 과거 사건을 되살리지 않는다.

없는 대상/권한 소멸은 읽기 트랜잭션이 롤백된 바깥에서 빈 결과로 처리한다. DB 장애를 자격 없음으로 숨기지 않는다. 반환 뒤 전송이 시작된 시점의 철회·배달 회수까지 보장하지 않는다.

## 검증

Java25 / PostgreSQL16 전용 DB `bb_b_email_eligibility_20261122`, **50/50 통과**, 실패·오류·건너뜀0.

| 범위 | 건수 |
| --- | --- |
| 신규 실제 DB · TaskRequestEmailEligibilityDatabaseTest | 7 |
| 신규 단위 · TaskRequestEmailEligibilityTest | 3 |
| 기존 업무 전환 · TaskWorkflowServiceTest | 23 |
| 인증 MockMvc+실제 DB · NotificationPresentationHttpTest | 6 |
| 기존 메일 내용/정책 · EmailNotificationContentTest | 8 |
| 발송 없는 설정 · MailSettingsTest | 3 |

현재 독립 확인자만 허용, 본인/담당자/외부인 제외, 설정 꺼짐/빈 목록, 계정 주소 변경과 잘못된 주소, 관찰자 전환/팀 탈퇴, 취소 후 재요청의 옛 key 거절, 요청자 변경, TODO/DONE/이미 승인, 없는 업무/사용자/프로젝트·다른 프로젝트 업무, 담당 변경, 조회 전후 업무 시각 불변·앱 알림/발송 대기 추가0건을 확인했다. 유효 입력의 기본 비활성 시 트랜잭션/저장소를 호출하지 않는 것과 업무 조회 중 DB 장애 전파도 단위 검사했다. 자료는 커밋된 합성 프로젝트이며 전용 DB에 남긴다. 실제 업무 삭제 API나 실제 회원 자료는 사용하지 않았다.

초기 실행은49/50이었다. view의 REQUIRED 트랜잭션에서 발생한 NotFound를 내부에서 잡으면 rollback-only가 남아 커밋 시 UnexpectedRollbackException이 됐다. 별도 읽기 TransactionTemplate 밖에서 예상 도메인 예외를 처리하도록 고쳐 없는 대상은 안전하게 건너뛰고 DB 장애는 계속 전파했다. 두 번째49/50은 반복 실행 중 고정 합성 이메일이 전용 DB의 기존 합성 계정과 중복된 fixture 문제였다. 잘못된 주소의 검사 의미를 유지하면서 UUID를 넣고 전체50건을 다시 실행해 통과했다. 검사를 삭제하거나 기존 서비스의 예외/권한 규칙을 바꾸지 않았다.

## 후속·미검증

- 자동 worker·스케줄·SMTP·실제 Gmail/Mailpit·브라우저·운영 적용은 수행하지 않았다. 로컬 전체 backend는 미실행이며 최종 PR CI로 별도 확인한다.
- 이 서비스는 업무 요청용이다. 검토 요청은 A의 결정 자격 재사용 경계를 #217 댓글로 확인 요청했다. 지원하지 않는 검토 요청을 이번 서비스로 판정해 처리 완료시키면 안 된다.
- 후속 worker가 #214의 대기를 #217로 획득하고 종류별 guard와 SMTP 어댑터를 연결해야 한다. 이 PR만으로 메일 발송 기능이 완성되지는 않는다. 초기 Draft 뒤 11/23 최신 main·전체 CI 확인 후 독립 조회 단위로 Ready 검토를 요청하며 MAIL_ENABLED는 켜지 않는다.
- A 검토 요청: 기존 수신 정책·메일 설정 소비, 독립 읽기 트랜잭션과 예외 처리, 업무 조회 권한의 재사용, 검토 결정 자격 조회의 재사용 방법. 새 API/SQL 번호를 임의로 추가하지 않았다.
