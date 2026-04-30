-- 경보 해제 필드 추가
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS resolved_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolve_reason VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_alerts_project_unresolved
    ON alerts(project_id) WHERE resolved_at IS NULL;
