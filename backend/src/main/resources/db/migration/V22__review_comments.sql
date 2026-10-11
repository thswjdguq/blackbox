-- K-11 피드백 코멘트와 K-12 피드백의 업무 전환.
-- 상태(OPEN·REFLECTION_PENDING·RESOLVED)는 저장하지 않는다. 해결 정보와 연결 업무의 상태로 조회할 때 계산한다(K-11 3장).

-- 코멘트가 연결 업무를 (업무, 제출물, 프로젝트)로 가리키려면 참조되는 쪽에 유일 제약이 필요하다. 기존 행은 바뀌지 않는다.
ALTER TABLE tasks ADD CONSTRAINT uq_task_deliverable_scope UNIQUE (id, deliverable_id, project_id);

CREATE TABLE review_comments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        UUID NOT NULL,
    deliverable_id    UUID NOT NULL,
    review_id         UUID NOT NULL,
    author_id         UUID NOT NULL REFERENCES users(id),
    content           VARCHAR(2000) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_by       UUID REFERENCES users(id),
    resolved_at       TIMESTAMPTZ,
    resolution_reason VARCHAR(1000),
    linked_task_id    UUID,
    -- 코멘트의 회차·제출물·프로젝트가 서로 맞는지 DB가 보장한다.
    CONSTRAINT fk_review_comment_round FOREIGN KEY (review_id, deliverable_id, project_id)
        REFERENCES review_rounds(id, deliverable_id, project_id),
    -- 같은 제출물의 업무만 연결된다. 연결된 업무를 다른 제출물로 옮기거나 연결을 해제하는 것도 이 외래키가 막는다.
    -- 업무가 지워지면 연결만 비우고 피드백과 해결 기록은 남긴다(K-12).
    CONSTRAINT fk_review_comment_task FOREIGN KEY (linked_task_id, deliverable_id, project_id)
        REFERENCES tasks(id, deliverable_id, project_id) ON DELETE SET NULL (linked_task_id),
    -- 업무 하나는 피드백 하나에만 연결된다.
    CONSTRAINT uq_review_comment_task UNIQUE (linked_task_id),
    -- 해결자와 해결 시각은 함께 있거나 함께 없다. 사유는 해결된 코멘트에만 있고, 해결돼도 비어 있을 수 있다.
    CONSTRAINT ck_review_comment_resolved CHECK ((resolved_by IS NULL) = (resolved_at IS NULL)),
    CONSTRAINT ck_review_comment_reason CHECK (resolved_at IS NOT NULL OR resolution_reason IS NULL)
);
-- 회차별 목록과 제출물 전체의 미해결 수 조회용
CREATE INDEX idx_review_comments_review ON review_comments(review_id);
CREATE INDEX idx_review_comments_deliverable ON review_comments(deliverable_id);
