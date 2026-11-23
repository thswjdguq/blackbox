# B · 이메일 발송 대기 획득과 결과 저장

- 기준 main `a74651ba` / K-41 0.4 1~6장 AGREED / 2026-11-22.
- B 소유 record·repository·DB 테스트만 추가한다. V39·기존 migration·설정·API·A/C 파일은 변경하지 않는다.

## 구현

EmailDeliveryRepository.claim은 커밋된 PENDING과 2분 lease가 끝난 PROCESSING을 FOR UPDATE SKIP LOCKED로 최대25건 획득한다. 짧은 REQUIRES_NEW 트랜잭션에서 claimed_at/attempt_count를 함께 갱신하며 반환할 때 잠금을 해제한다. 이미3회 시도하고 lease가 끝난 행은 FAILED/MAX_ATTEMPTS로 끝내고4번째 획득을 반환하지 않는다.

complete는 id·PROCESSING·claimed_at·attempt_count·미만료 시각을 함께 대조한다. 만료되거나 재획득된 worker의 결과와 중복 결과는0행으로 거절한다. 성공은 SENT/sent_at, 일시 실패/전달 불명은30초×시도 횟수 뒤 재시도(최대3회), 인증/영구 실패는FAILED, 수신 조건 소멸은SKIPPED다. 자유 문자열 대신 enum 분류 코드만 저장한다.

EmailDeliveryLease는 JPA 저장 대상이 아닌 불변 record다. 원문 주소·본문 없이 기존 V39 스냅숏과 획득 식별자를 반환하며 toString에는 대기 id와 시도 횟수만 넣는다. #214의 예약 저장 파일은 그대로 둔다.

## 검증

Java25.0.3 / PostgreSQL16 전용 DB `bb_b_email_lease_20261122`. 관련 **34/34 통과**, 실패·오류·건너뜀0.

| 범위 | 건수 |
| --- | --- |
| EmailDeliveryRepositoryDatabaseTest 실제 DB | 9 |
| NotificationPresentationHttpTest DB·인증 MockMvc | 6 |
| EmailNotificationContentTest·NotificationPresentationFactoryTest | 16 |
| MailSettingsTest 발송 없는 설정 | 3 |

DB에서 확인한 내용: 미래 대기 제외·25건 상한, 두 연결의 동시 획득 중복 없음, 다른 연결이 잠근 행 건너뛰기와 미커밋 행 비노출, lease 만료/재획득 후 이전 결과 거절, 중복 성공 시각 보존, 일시 실패 지연·최대3회,3번째 프로세스 중단 뒤 최종 실패, 영구/인증 실패와 조건 소멸의 즉시 종결, 전달 불명의 지연, caller 롤백과 별개로 획득 커밋, 시도 횟수 식별자 불일치 거절. fixture는 합성 계정/프로젝트만 사용한다. 테스트 종료 때 본인이 만든 대기의 미완료 상태만 SKIPPED로 끝내고 자료는 전용 DB에 남긴다.

최초33건에서31건 통과·2건 실패했다. 저장소 프록시가 IllegalArgumentException을 InvalidDataAccessApiUsageException으로 변환하는 공개 경계를 테스트가 반영하지 못했다. 거절 검사를 변환된 타입과 원인 예외 모두 확인하도록 고쳤고 별도 트랜잭션/시도 횟수 식별자 검사도 추가해 전체34건을 다시 실행했다. 운영 코드의 거절 조건을 없애지 않았다.

## 미구현·미검증·다음

- 자동 worker·스케줄·SMTP 호출·운영 활성화를 추가하지 않았다. 실제 Gmail·Mailpit·브라우저·운영 서버 검사는 없다. 로컬 전체 backend는 미실행이며 PR 전체 CI를 별도 기록한다.
- 실제 수신 조건 재확인과 Transport 결과5종→저장 분류 매핑, Message-ID 재사용, 자동 실행·enabled/빈 목록 gate는 후속 worker/SMTP 결합 단위다. 이 repository 자체는 발송 설정을 대신 판단하지 않으며 후속 worker가 조건 확인 뒤 호출해야 한다.
- 전체 발송은 #214 예약 저장과 A SMTP 어댑터가 필요하다. 이 PR은 현재 main의 V39만으로 컴파일·검사할 수 있어 소스 의존은 없다. 초기 Draft 뒤 11/23 최신 main·전체 CI를 확인해 저장소 단위로 Ready 검토를 요청한다. 전체 메일 발송 완료를 뜻하지 않는다.
- A 검토 요청: 기존 V39 제약·원자 SQL·REQUIRES_NEW 경계·행 잠금·시각 정밀도·최종 만료 종결. 기존 DDL/마이그레이션 번호 변경은 필요 없다.
- SMTP 수락 뒤 SENT 기록 전 중단은 중복 배달이 가능하다. 같은 delivery id의 Message-ID를 후속 어댑터가 쓰며 정확히 한 번 배달을 보장하지 않는다.
