# B · 파일 집계 대상과 검토 집계의 분리

- 기준 main `a5999476`, 최종 main 반영 `796288de`(#179/V38 포함)
- 브랜치 `codex/B-score-snapshot-scope`, K-40 0.3 AGREED, #183 A 리뷰 1·3번 반영
- #184와 독립이며 B 점수 서비스/검사/개인 문서만 바꿨다. SQL·API 응답·판정·criteriaVersion은 그대로다.

## 변경

ScoreService가 이미 읽은 현재 프로젝트 기여자 목록을 ScoreSnapshotService로 넘긴다. 파일 집계의 users는 그 목록에서 정하고 검토 판독기 결과의 key를 쓰지 않는다. 검토 결과에 없는 기여자도 파일을 세며 검토 횟수만 0으로 둔다. 관찰자와 검토 결과의 범위 밖 key는 결과에 넣지 않는다. 새 멤버 목록 조회나 사용자별 파일 조회를 늘리지 않는다.

ScoreSnapshotService/ScoreReviewActivityReader의 MANDATORY에 붙어 있던 readOnly를 제거하고 호출자의 트랜잭션 속성을 따른다고 명시했다. 읽기 전용 트랜잭션을 별도로 만드는 변경은 아니다.

## 검사

Java25.0.3·Gradle9.8.2·PostgreSQL16 전용 DB `bb_b_score_scope_20261116`에서 **27/27**, 실패·오류·건너뜀0(2분36초).

- ScoreSnapshotScopeTest 단위3: 검토 판독기가 0회 행을 생략해도 파일 집계 유지, 기여자 명단을 쿼리 입력으로 사용하고 관찰자 key 제외, 기여자 없으면 쿼리 없음.
- ScoreSnapshotHttpTest 실제 DB/인증 HTTP4: 기존 null/권한·JSONB/같은 시점 저장·롤백3에 파일만1개/검토0/파일참여true/기존NONE판정/GET 재조회1을 추가.
- 기존 ScoreContributorPolicyTest3·ScoreAlertAccessTest12 단위, ScoreReviewActivityReaderTest3 실제 DB, ScoreContributorHttpTest2 실제 DB/HTTP.

main의 #179와 V38을 일반 merge한 뒤 핵심 단위3 + HTTP4를 다시 돌려 **7/7**, 실패·오류·건너뜀0(2분15초). 앞선27건과 구별한 추가 검증이다. 새 migration이 있는 최신 컨텍스트에서도 파일 집계를 확인했다. 기존 main SQL24개 보존·37개 migration 번호 중복 없음·diff 검사도 통과했다.

합성 자료는 검사 트랜잭션으로 롤백한다. 운영 DB·업로드·비밀값을 건드리지 않았다. 로컬 전체 backend·브라우저·유료 AI·PDF 실연동은 수행하지 않았다. 최종 전체 CI는 PR에서 확인·기록한다.

## 경계와 다음 작업

판정과 저장 구조는 바꾸지 않아 기준 버전은 올리지 않았다. period는 여전히 현재 가입 시각 정보이며 회의 기준 후속 전에는 모든 횟수의 집계 기간으로 표시하지 않는다. #186은 period를 표시하지 않고 A도 PDF 표시를 미루기로 확인했다.

label/description은 계산 때의 문구를 저장하는 snapshot의 일부다. 다음 재계산 전 옛 문구가 남는 것은 의도된 동작이며 소비자는 서버 문구를 따른다. 같은 계산에서 검토 판독을 두 번 하는 문제는 #184 뒤 예약/중복 제거 단계에서 정리한다. A 서버 리뷰와 작성자 외 병합이 필요하다.
