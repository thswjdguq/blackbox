-- main V25에 이미 있던 DONE 기록은 승인자를 만들어내지 않고 보존한다.
-- 새 완료 전환/INSERT와 기존 승인 기록 제거는 막되, 이전 DONE의 제목·연결 수정은 허용한다.
ALTER TABLE tasks DROP CONSTRAINT task_done_requires_approval;

CREATE FUNCTION enforce_task_completion_transition() RETURNS trigger AS $$
BEGIN
    IF NEW.status = 'DONE' AND NEW.completion_approved_at IS NULL THEN
        IF TG_OP = 'UPDATE' THEN
            IF OLD.status = 'DONE' AND OLD.completion_approved_at IS NULL THEN
                RETURN NEW;
            END IF;
        END IF;
        RAISE EXCEPTION 'task_done_requires_approval: new completion requires approval'
            USING ERRCODE = '23514', CONSTRAINT = 'task_done_requires_approval';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_task_completion_transition
    BEFORE INSERT OR UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION enforce_task_completion_transition();
