-- K-30 AI 에이전트: 실행 기록, 제안 카드, 채택으로 만들어진 것.
-- 에이전트는 읽기만 한다. 제출물·요구사항·업무는 사람이 제안 카드를 채택할 때 만들어진다(K-30 1장).
-- 실행 종류(skill), 카드 종류(kind), 채택 대상의 종류(target_type)는 코드에서 등록으로 늘어난다.
-- 그래서 값 목록을 제약으로 묶지 않고, 이 파일에도 적지 않는다. 상태(status)만 제약으로 묶는다.

CREATE TABLE agent_runs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    skill        VARCHAR(40) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    error        VARCHAR(500),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ,
    CONSTRAINT ck_agent_run_status CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    -- 끝난 실행에만 끝난 시각이 있다
    CONSTRAINT ck_agent_run_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL)),
    -- 제안 카드가 실행과 같은 프로젝트에 속하도록 복합 외래키의 대상이 된다
    CONSTRAINT uq_agent_run_project UNIQUE (id, project_id)
);
-- 진행 중인 실행 확인과 시간당 횟수 세기에 쓴다
CREATE INDEX idx_agent_runs_project_started ON agent_runs (project_id, started_at DESC);

CREATE TABLE agent_proposals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL,
    run_id      UUID NOT NULL,
    kind        VARCHAR(40) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    title       VARCHAR(255) NOT NULL,
    rationale   TEXT NOT NULL,
    content     JSONB NOT NULL,   -- 지금 내용. 사람이 고친 것이 들어간다
    original    JSONB NOT NULL,   -- 모델이 만든 원본. 바꾸지 않는다
    decided_by  UUID REFERENCES users(id),
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_agent_proposal_run FOREIGN KEY (run_id, project_id) REFERENCES agent_runs (id, project_id),
    CONSTRAINT ck_agent_proposal_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    -- 결정된 카드에만 결정한 사람과 시각이 함께 있다
    CONSTRAINT ck_agent_proposal_decided CHECK (
        (status = 'PENDING') = (decided_at IS NULL) AND (decided_by IS NULL) = (decided_at IS NULL))
);
CREATE INDEX idx_agent_proposals_project ON agent_proposals (project_id, created_at DESC);

-- 채택으로 만들어진 기록. 그 기록이 나중에 지워져도 이 줄은 남도록 대상에는 외래키를 걸지 않는다
CREATE TABLE agent_proposal_results (
    proposal_id UUID NOT NULL REFERENCES agent_proposals(id),
    target_type VARCHAR(40) NOT NULL,
    target_id   UUID NOT NULL,
    PRIMARY KEY (proposal_id, target_type, target_id)
);
-- "이 기록이 제안 카드에서 만들어졌는가"를 대상으로 찾는다
CREATE INDEX idx_agent_results_target ON agent_proposal_results (target_type, target_id);
