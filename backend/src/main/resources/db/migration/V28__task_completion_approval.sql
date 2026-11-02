-- 별도 UI 시연 브랜치 제안. main 반영 전 A와 번호·상태 계약을 조정한다.
ALTER TABLE tasks ADD COLUMN predecessor_id UUID;
ALTER TABLE tasks ADD COLUMN attachment_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN completion_requested_at TIMESTAMPTZ;
ALTER TABLE tasks ADD COLUMN completion_requested_by UUID REFERENCES users(id);
ALTER TABLE tasks ADD COLUMN completion_approved_at TIMESTAMPTZ;
ALTER TABLE tasks ADD COLUMN completion_approved_by UUID REFERENCES users(id);
ALTER TABLE tasks ADD COLUMN completion_rejection_reason VARCHAR(2000);
ALTER TABLE tasks ADD CONSTRAINT fk_task_predecessor_project FOREIGN KEY (predecessor_id, project_id) REFERENCES tasks(id, project_id);
ALTER TABLE tasks ADD CONSTRAINT task_not_own_predecessor CHECK (predecessor_id IS NULL OR predecessor_id <> id);
ALTER TABLE tasks ADD CONSTRAINT task_request_pair CHECK ((completion_requested_at IS NULL) = (completion_requested_by IS NULL));
ALTER TABLE tasks ADD CONSTRAINT task_approval_pair CHECK ((completion_approved_at IS NULL) = (completion_approved_by IS NULL));
-- 과거 완료 행은 보존한다. 새 행과 수정되는 행은 승인 없는 DONE을 허용하지 않는다.
ALTER TABLE tasks ADD CONSTRAINT task_done_requires_approval CHECK (status <> 'DONE' OR completion_approved_at IS NOT NULL) NOT VALID;
CREATE INDEX idx_tasks_predecessor ON tasks(predecessor_id);
