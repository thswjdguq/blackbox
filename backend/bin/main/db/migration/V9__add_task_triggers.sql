-- tasks updated_at 자동 갱신 (update_updated_at() 함수는 V8에서 이미 생성)
CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
