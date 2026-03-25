-- 기여도 점수 (주기적 재계산)
CREATE TABLE contribution_scores (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id),
    user_id        UUID NOT NULL REFERENCES users(id),
    -- 항목별 점수 (0~150, 팀 평균=100 기준)
    git_score      DECIMAL(5,2) NOT NULL DEFAULT 0,
    doc_score      DECIMAL(5,2) NOT NULL DEFAULT 0,
    meeting_score  DECIMAL(5,2) NOT NULL DEFAULT 0,
    task_score     DECIMAL(5,2) NOT NULL DEFAULT 0,
    -- 종합 점수
    total_score    DECIMAL(5,2) NOT NULL DEFAULT 0,
    -- 적용된 가중치 스냅샷
    weight_git     DECIMAL(3,2) NOT NULL DEFAULT 0.30,
    weight_doc     DECIMAL(3,2) NOT NULL DEFAULT 0.25,
    weight_meeting DECIMAL(3,2) NOT NULL DEFAULT 0.20,
    weight_task    DECIMAL(3,2) NOT NULL DEFAULT 0.25,
    calculated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, user_id)
);

-- 경보
CREATE TABLE alerts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    user_id    UUID REFERENCES users(id),  -- NULL이면 팀 전체 경보
    alert_type VARCHAR(30) NOT NULL,       -- CRUNCH_TIME | FREE_RIDE | DROPOUT
                                            -- OVERLOAD | TAMPER | GAMING_SUSPECT
    severity   VARCHAR(10) NOT NULL,        -- LOW | MEDIUM | HIGH | CRITICAL
    message    TEXT NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 가중치 설정 (교수용)
CREATE TABLE weight_configs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id),
    professor_id   UUID NOT NULL REFERENCES users(id),
    weight_git     DECIMAL(3,2) NOT NULL DEFAULT 0.30,
    weight_doc     DECIMAL(3,2) NOT NULL DEFAULT 0.25,
    weight_meeting DECIMAL(3,2) NOT NULL DEFAULT 0.20,
    weight_task    DECIMAL(3,2) NOT NULL DEFAULT 0.25,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT weights_sum CHECK (
        weight_git + weight_doc + weight_meeting + weight_task = 1.00
    )
);

-- 가중치 변경 이력 (일관성 추적)
CREATE TABLE weight_change_log (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    changed_by UUID NOT NULL REFERENCES users(id),
    old_weights JSONB NOT NULL,
    new_weights JSONB NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 수동 작업 신고 (오프라인 작업 보완)
CREATE TABLE manual_work_reports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID NOT NULL REFERENCES projects(id),
    user_id          UUID NOT NULL REFERENCES users(id),
    description      TEXT NOT NULL,
    category         VARCHAR(30) NOT NULL,  -- 기능 | 문서 | 디자인 | 조사
    estimated_hours  DECIMAL(4,1),
    file_vault_id    UUID REFERENCES file_vault(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 피어리뷰 (확장 2)
CREATE TABLE peer_reviews (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES projects(id),
    reviewer_id   UUID NOT NULL REFERENCES users(id),
    reviewee_id   UUID NOT NULL REFERENCES users(id),
    score         INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment       TEXT,
    review_round  INTEGER NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_review CHECK (reviewer_id != reviewee_id),
    UNIQUE (project_id, reviewer_id, reviewee_id, review_round)
);
