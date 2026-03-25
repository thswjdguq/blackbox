CREATE TABLE activity_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID NOT NULL REFERENCES projects(id),
    user_id          UUID NOT NULL REFERENCES users(id),
    source           VARCHAR(20)  NOT NULL,  -- PLATFORM | GITHUB | GOOGLE_DRIVE | MANUAL
    action_type      VARCHAR(50)  NOT NULL,  -- COMMIT | PR_CREATE | PR_REVIEW |
                                              -- FILE_EDIT | FILE_CREATE | COMMENT |
                                              -- TASK_CREATE | TASK_COMPLETE |
                                              -- MEETING_ATTEND | CHECKIN | MANUAL_REPORT
    metadata         JSONB,                  -- 상세 데이터 (커밋 메시지, diff 크기 등)
    external_id      VARCHAR(255),           -- 외부 시스템 ID (commit SHA 등)
    trust_level      DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 1.0=자동, 0.7=수동
    occurred_at      TIMESTAMPTZ NOT NULL,   -- 실제 발생 시각
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- AI 분석 결과 (확장 2)
    quality_score    DECIMAL(3,2),           -- 0.00 ~ 1.00
    quality_reason   TEXT,
    analysis_method  VARCHAR(20)             -- RULE_BASED | AI_CLAUDE
);

CREATE INDEX idx_activity_project_user ON activity_logs (project_id, user_id);
CREATE INDEX idx_activity_occurred     ON activity_logs (occurred_at DESC);
CREATE INDEX idx_activity_source       ON activity_logs (source, action_type);
