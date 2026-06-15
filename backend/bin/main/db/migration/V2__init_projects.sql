CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    course_name VARCHAR(255),
    semester    VARCHAR(20),
    start_date  DATE,
    end_date    DATE,
    invite_code VARCHAR(8) UNIQUE,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE project_members (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id),
    role                VARCHAR(20) NOT NULL DEFAULT 'MEMBER',  -- LEADER | MEMBER | OBSERVER
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consent_platform    BOOLEAN NOT NULL DEFAULT FALSE,
    consent_github      BOOLEAN NOT NULL DEFAULT FALSE,
    consent_drive       BOOLEAN NOT NULL DEFAULT FALSE,
    consent_ai_analysis BOOLEAN NOT NULL DEFAULT FALSE,
    consented_at        TIMESTAMPTZ,
    UNIQUE (project_id, user_id)
);

-- OAuth 토큰 (확장 1 — 암호화 저장)
CREATE TABLE oauth_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    provider        VARCHAR(20) NOT NULL,  -- GITHUB_APP | GOOGLE
    access_token    TEXT NOT NULL,         -- AES-256 암호화
    refresh_token   TEXT,                  -- AES-256 암호화
    installation_id BIGINT,                -- GitHub App installation ID
    scope           TEXT,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider)
);

-- 연동 리포지터리/드라이브 (확장 1)
CREATE TABLE integrations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider       VARCHAR(20) NOT NULL,   -- GITHUB_APP | GOOGLE_DRIVE
    external_id    VARCHAR(255) NOT NULL,  -- repo full name | drive folder ID
    external_name  VARCHAR(255),
    webhook_id     VARCHAR(255),
    webhook_expiry TIMESTAMPTZ,
    last_synced    TIMESTAMPTZ,
    sync_status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PAUSED | ERROR
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
