-- Google Calendar OAuth 토큰 저장
CREATE TABLE google_calendar_tokens (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token  TEXT        NOT NULL,
    refresh_token TEXT,
    token_expiry  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_google_calendar_tokens_user UNIQUE (user_id)
);

CREATE INDEX idx_gct_user_id ON google_calendar_tokens(user_id);

-- updated_at 자동 갱신 트리거
CREATE TRIGGER trg_gct_updated_at
    BEFORE UPDATE ON google_calendar_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
