-- K-40 0.3 기여도 집계의 스냅숏. 판정(네 불리언, participation_level), 항목 목록, 기준 버전, 집계 기간을
-- 한 번의 재계산에서 함께 저장하고 조회는 저장된 값을 그대로 돌려준다(K-40 2장).
-- 세 칸 모두 비워 둘 수 있다. 이 버전 전에 계산된 행은 첫 재계산 전까지 NULL이며, 옛 값에 새 기준 버전을 붙이지 않는다.
ALTER TABLE contribution_scores
    ADD COLUMN items            JSONB,
    ADD COLUMN criteria_version VARCHAR(32),
    ADD COLUMN period_from      TIMESTAMPTZ;
