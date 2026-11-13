-- K-31 0.3 과제 안내문 공간(9장). 안내문은 프로젝트 단위로 두고, 제출물과 제안 카드가 연결 표로 가리킨다.
-- 번호는 기능의 병합 순서대로다: V36 확정 취소 → V37 기여도 집계 저장 → V38 이 마이그레이션.

CREATE TABLE project_briefs (
    id             UUID PRIMARY KEY,
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    body           TEXT NOT NULL,
    -- body의 SHA-256(hex). 카드의 근거가 만들어진 뒤 본문이 바뀌었는지 가리고, 같은 본문을 다시 저장하지 않게 찾는다
    body_hash      VARCHAR(64) NOT NULL,
    -- 파일 금고에는 삭제가 없으므로 일반 외래 키다
    source_file_id UUID REFERENCES file_vault(id),
    created_by     UUID NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_by     UUID NOT NULL REFERENCES users(id),
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_project_briefs_project_hash ON project_briefs (project_id, body_hash);

CREATE TABLE deliverable_briefs (
    deliverable_id UUID NOT NULL REFERENCES deliverables(id) ON DELETE CASCADE,
    -- 걸린 안내문은 DB도 지우지 못한다. 검사가 문장 끝에 있으므로(NO ACTION) 프로젝트 삭제처럼
    -- 제출물·연결·안내문이 한 문장에서 함께 지워질 때는 지우는 순서와 관계없이 된다
    brief_id       UUID NOT NULL REFERENCES project_briefs(id) ON DELETE NO ACTION,
    linked_by      UUID NOT NULL REFERENCES users(id),
    linked_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (deliverable_id, brief_id)
);

-- 안내문마다 쓰는 제출물을 찾는다(삭제 막기, usage)
CREATE INDEX idx_deliverable_briefs_brief ON deliverable_briefs (brief_id);

-- 제안 카드가 근거로 쓰는 안내문. 검토 대기 카드가 있으면 서비스가 삭제를 막는다.
-- 결정한 카드는 막지 않고 근거 원문은 카드 content에 남으므로 양쪽 모두 연쇄 삭제다
CREATE TABLE agent_proposal_briefs (
    proposal_id UUID NOT NULL REFERENCES agent_proposals(id) ON DELETE CASCADE,
    brief_id    UUID NOT NULL REFERENCES project_briefs(id) ON DELETE CASCADE,
    PRIMARY KEY (proposal_id, brief_id)
);

CREATE INDEX idx_agent_proposal_briefs_brief ON agent_proposal_briefs (brief_id);
