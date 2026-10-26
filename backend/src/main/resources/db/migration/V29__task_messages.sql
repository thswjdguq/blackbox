-- 별도 UI 환경의 제안 번호. main 반영 전 A가 번호와 보존 정책을 검토한다.
CREATE TABLE task_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    task_id UUID NOT NULL,
    author_id UUID NOT NULL REFERENCES users(id),
    recipient_id UUID REFERENCES users(id),
    parent_id UUID,
    file_id UUID,
    body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('COMMENT', 'REQUEST', 'REPLY')),
    answered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_task_message_scope UNIQUE (id, task_id, project_id),
    CONSTRAINT fk_message_task FOREIGN KEY (task_id, project_id) REFERENCES tasks(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_message_parent FOREIGN KEY (parent_id, task_id, project_id) REFERENCES task_messages(id, task_id, project_id),
    CONSTRAINT fk_message_file FOREIGN KEY (task_id, file_id) REFERENCES task_files(task_id, file_id),
    CONSTRAINT message_shape CHECK (
        (kind = 'COMMENT' AND recipient_id IS NULL AND parent_id IS NULL AND answered_at IS NULL) OR
        (kind = 'REQUEST' AND recipient_id IS NOT NULL AND recipient_id <> author_id AND parent_id IS NULL) OR
        (kind = 'REPLY' AND recipient_id IS NULL AND parent_id IS NOT NULL AND answered_at IS NULL)
    )
);
CREATE INDEX idx_task_messages_thread ON task_messages(task_id, created_at DESC, id DESC);
CREATE INDEX idx_task_messages_pending ON task_messages(project_id, recipient_id) WHERE kind = 'REQUEST' AND answered_at IS NULL;
