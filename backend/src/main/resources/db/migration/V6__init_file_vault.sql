-- 위변조 방지 금고 테이블 (수정 불가)
CREATE TABLE file_vault (
    id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID    NOT NULL REFERENCES projects(id),
    uploader_id  UUID    NOT NULL REFERENCES users(id),
    file_name    VARCHAR(255) NOT NULL,
    file_hash    VARCHAR(64)  NOT NULL,   -- SHA-256 (64자)
    file_size    BIGINT       NOT NULL,
    storage_path TEXT         NOT NULL,   -- /data/uploads/{projectId}/...
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    is_immutable BOOLEAN      NOT NULL DEFAULT TRUE,
    version      INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT no_update_hash CHECK (is_immutable = TRUE)
);

-- file_vault UPDATE/DELETE 차단 트리거
CREATE OR REPLACE FUNCTION prevent_vault_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'file_vault 레코드는 수정/삭제할 수 없습니다';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER vault_immutable
    BEFORE UPDATE OR DELETE ON file_vault
    FOR EACH ROW EXECUTE FUNCTION prevent_vault_modification();

-- 변조 감지 이력 테이블
CREATE TABLE tamper_detection_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vault_id      UUID NOT NULL REFERENCES file_vault(id),
    detected_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    original_hash VARCHAR(64) NOT NULL,
    new_hash      VARCHAR(64) NOT NULL,
    detector_type VARCHAR(20) NOT NULL,          -- REUPLOAD | SCHEDULED_CHECK
    status        VARCHAR(20) NOT NULL DEFAULT 'FLAGGED'  -- FLAGGED | REVIEWED | DISMISSED
);
