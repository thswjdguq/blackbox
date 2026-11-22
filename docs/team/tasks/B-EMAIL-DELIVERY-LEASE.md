# B-EMAIL-DELIVERY-LEASE · 발송 대기 획득과 결과 저장

- 담당 B / 검토 A / 기준 main a74651ba / 2026-11-22 / K-41 0.4 1~6장 AGREED.
- 목표: V39의 커밋된 대기를 짧은 트랜잭션으로 획득하고, lease·최대3회·오래된 결과 거절을 실제 DB에서 확인한다.
- 허용: B의 신규 EmailDeliveryLease record·EmailDeliveryRepository와 대응 DB 테스트·개인 작업서/인수인계.
- 금지: migration·DB 제약/번호·A/C 코드·라이브러리·설정·스케줄 등록·SMTP/실제 수신자/운영 접근. #214의 예약 저장 파일은 수정하지 않는다.
- 범위: 기본25건 상한, 2분 lease, FOR UPDATE SKIP LOCKED 원자 획득, id/claimed_at/시도 횟수/미만료 비교, 재시도 지연, 최대3회, 마지막 시도의 lease 만료 종결. 수신 자격 판정·SMTP 어댑터·자동 worker는 후속이며 이 PR만으로 발송하지 않는다.
- 검증: 별도 PostgreSQL DB에서 동시 두 획득·다른 연결의 미커밋/잠금 행 제외·lease 회수·이전 결과 거절·결과 중복·재시도 상한·수신 불가 종결·스냅숏을 검사한다. SQL/설정 변경이 없으므로 기존 V39를 그대로 사용한다.
- #214는 A 승인 후 별도 대기 중이다. 이 PR은 그 파일에 코드 의존하지 않고 현재 main의 V39를 사용하며 전체 worker가 준비되기 전 Draft로 제출한다.
- 실제 PostgreSQL 동시성/lease9건과 알림 HTTP6·문장/정책16·메일 설정3건, 합계34/34 통과. 새 자동 발송은 등록하지 않는다.
