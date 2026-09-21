CREATE TABLE deliverables (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date DATE NOT NULL,
    submission_method VARCHAR(500),
    owner_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id, project_id)
);
CREATE INDEX idx_deliverables_project ON deliverables(project_id, due_date);
CREATE TABLE deliverable_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deliverable_id UUID NOT NULL REFERENCES deliverables(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id, deliverable_id)
);
CREATE INDEX idx_requirements_deliverable ON deliverable_requirements(deliverable_id);
ALTER TABLE tasks ADD COLUMN deliverable_id UUID;
ALTER TABLE tasks ADD COLUMN requirement_id UUID;
ALTER TABLE tasks ADD COLUMN completion_criteria VARCHAR(2000);
ALTER TABLE tasks ADD CONSTRAINT fk_task_deliverable_project
    FOREIGN KEY (deliverable_id, project_id) REFERENCES deliverables(id, project_id);
ALTER TABLE tasks ADD CONSTRAINT fk_task_requirement_deliverable
    FOREIGN KEY (requirement_id, deliverable_id) REFERENCES deliverable_requirements(id, deliverable_id);
ALTER TABLE tasks ADD CONSTRAINT ck_task_requirement_parent CHECK (requirement_id IS NULL OR deliverable_id IS NOT NULL);
CREATE INDEX idx_tasks_deliverable ON tasks(deliverable_id);
CREATE INDEX idx_tasks_requirement ON tasks(requirement_id);
