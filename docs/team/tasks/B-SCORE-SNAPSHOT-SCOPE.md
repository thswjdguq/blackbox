# B-SCORE-SNAPSHOT-SCOPE · 파일 집계의 기여자 범위 분리

- 담당 B / 검토 A / 기준 main `a5999476` / 사용자 요청 2026-11-16
- 근거: #183 A 승인 의견 1·3번. K-40 0.3 AGREED 유지.
- 허용: ScoreService, ScoreSnapshotService, ScoreReviewActivityReader, 대응 B 점수 검사와 개인 문서.
- 금지: A/C 소유 파일·SQL/migration·외부 API 계약·점수 기준 변경·운영 데이터 변경.

파일을 셀 사용자는 검토 집계 결과의 key가 아니라 계산기가 이미 확보한 현재 기여자 목록에서 정한다. 검토 결과에 없는 기여자도 파일은 세고 검토는 0으로 둔다. 관찰자는 제외한다. MANDATORY 조회에 붙은 효과 없는 readOnly를 제거하고 호출자의 트랜잭션 속성을 따른다는 사실을 명시한다.

검토 횟수가 없는 기여자의 파일이 빠지지 않는 단위 회귀와 파일만 1개인 팀원의 실제 HTTP/JSONB 재조회 회귀를 추가한다. 기존 점수·권한·검토 회귀도 전용 DB에서 확인한다. criteriaVersion·판정·응답 모양은 바꾸지 않는다. 같은 재계산의 중복 검토 조회 제거와 커밋 뒤 계산 예약은 #184 뒤 별도 PR이다.
