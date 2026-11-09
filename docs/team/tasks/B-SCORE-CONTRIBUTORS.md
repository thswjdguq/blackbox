# B-SCORE-CONTRIBUTORS · 관찰자 기여도 계산 제외

- 담당 B / 검토 A / 관련 #134 8.4, #158, #163
- 기준 main `b2a26cec`(Java 25) / 사용자 요청: PR 확인 후 B 담당 작업 진행
- 허용: ScoreService의 계산 대상, 신규 ScoreContributorPolicyTest·ScoreContributorHttpTest, B 문서.
- 금지: 산식/임계값, snapshot·응답 필드, SQL/기존 migration, 기존 관찰자 자료 삭제, A/C 파일 수정.
- 목표: 현재 팀장·팀원만 계산·저장·경보 입력 대상으로 삼는다. 관찰자 기존 점수 행은 보존하며 조회의 기존 제외 규칙을 유지한다.
- 역할이 팀원으로 바뀌면 기존 행을 재사용해 다시 계산한다. 팀원에서 관찰자로 바뀌면 다음 계산에서 갱신하지 않는다.
- 검사: 관찰자/알 수 없는 역할의 집계 쿼리·저장 없음, 팀장·팀원 정상 계산, 역할 전환과 옛 행 보존을 실제 DB·HTTP로 확인한다.
- K-40 확대는 C 소비자 승인과 A의 migration 배정/구현 뒤 별도 진행한다.
