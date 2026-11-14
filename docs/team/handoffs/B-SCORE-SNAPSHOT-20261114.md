# B · 기여도 항목 저장·응답 연결

- 기준 main `830a99cf` / 브랜치 `codex/B-score-snapshot` / K-40 0.3 AGREED
- 선행 #177 검토 집계와 #178 V37 SQL은 main에 병합돼 있다. 이번 작업에서 SQL은 바꾸지 않았다.

## 변경

기존 네 참여 여부와 FULL/PARTIAL/NONE 임계값은 유지한다. 같은 재계산에서 `items` JSONB, `criteriaVersion`, `periodFrom`, UTC·마이크로초 `calculatedAt`을 함께 저장한다. GET은 저장된 값을 반환하며 옛 행에는 첫 재계산 전까지 새 칸을 null로 둔다.

파일은 서로 다른 이름 수로 세어 같은 이름의 버전 여러 개를 한 번으로 본다. 1개 이상이라는 기존 판정 의미는 같다. 검토는 #177의 여섯 항목을 합해 별도로 보이고 판정에 넣지 않는다. 항목의 passed는 방금 저장한 네 불리언을 그대로 쓴다.
`period.from`은 현재 가입 시각, `to`는 계산 시각이다. 횟수는 K-40 첫 기준대로 과거 기록을 포함한다. 특히 회의는 이 버전에서 프로젝트 전체이며 가입 뒤 회의만 세는 결함 수정은 다음 버전이다. 응답 설명에도 전체 회의임을 적었다.

## 검사

- Java 25.0.3 / Gradle 9.8.2 / Spring Boot 4.1 / PostgreSQL 16 새 전용 DB `bb_b_score_snapshot_20261114`.
- 신규 ScoreSnapshotHttpTest **3/3 통과**: 옛 null·접근 경계, 네 판정/항목 일치·파일 중복 버전·검토 분리·JSONB POST/GET 재조회·계산 시각·재계산 전 조회 고정, 호출자 트랜잭션 롤백.
- 기존 ScoreContributorHttpTest 2, ScoreContributorPolicyTest 3, ScoreAlertAccessTest 12, ScoreReviewActivityReaderTest 3, MainMigrationCompatibilityTest 2도 통과. 총 **25/25**, 실패·오류·건너뜀 0. 실제 실행 2분 18초.
- 첫 실행은 신규 테스트의 제네릭 반환값/오버로드가 모호해 컴파일 단계에서 실패했다. 타입을 명시한 후 같은 검사 전체를 재실행했다. 실패 검사를 지우거나 조건을 완화하지 않았다.
- HTTP 자료는 롤백했고 migration 검사는 이 전용 DB의 별도 합성 schema를 사용한다. 기존 24개 SQL 보존·전체 36개 번호 중복 없음 검사와 diff 검사 통과.
- 로컬 전체 backend·브라우저·유료 AI·운영 적용은 수행하지 않았다. 최종 head의 전체 CI는 PR에 별도 기록한다.

## 남은 일

A 서버 리뷰와 C의 실제 응답 소비자 확인, 작성자 외 병합이 필요하다. 경보의 검토 횟수/최근 활동 반영과 AFTER_COMMIT·프로젝트 잠금/프로젝트별 계산은 후속 PR이다. 현재 경보가 개선됐거나 K-40 전체가 구현됐다고 보고하지 않는다.
C 기여도 화면과 A PDF·AI 점수 도구는 이 응답을 받아 연결하며, 배포 전 행의 items/criteriaVersion/period null을 허용해야 한다.
