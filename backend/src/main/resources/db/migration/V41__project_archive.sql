-- K-60 프로젝트 보관. 보관된 프로젝트는 기록을 남긴 채 읽기 전용이 되고, 팀장이 해제하면 다시 쓸 수 있다.
-- 두 칸은 함께 있거나 함께 없다. 해제하면 둘 다 지운다(누가 해제했는지는 남기지 않는다).
ALTER TABLE projects
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES users(id),
    ADD CONSTRAINT projects_archived_shape CHECK ((archived_at IS NULL) = (archived_by IS NULL));
