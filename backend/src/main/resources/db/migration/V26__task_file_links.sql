-- 별도 UI 시연용 업무 자료 연결. main 반영 전 A와 번호·삭제 정책을 조정한다.
ALTER TABLE tasks ADD CONSTRAINT uq_task_id_project UNIQUE (id, project_id);
CREATE TABLE task_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    task_id UUID NOT NULL,
    file_id UUID NOT NULL,
    linked_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_file_task FOREIGN KEY (task_id, project_id) REFERENCES tasks(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_task_file_file FOREIGN KEY (file_id, project_id) REFERENCES file_vault(id, project_id),
    CONSTRAINT uq_task_file UNIQUE (task_id, file_id)
);
CREATE INDEX idx_task_files_task ON task_files(task_id, created_at);
