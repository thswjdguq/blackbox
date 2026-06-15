CREATE TABLE meetings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title        VARCHAR(255),
    meeting_date TIMESTAMPTZ NOT NULL,
    purpose      TEXT,
    notes        TEXT,
    decisions    TEXT,
    checkin_code VARCHAR(8) UNIQUE,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE meeting_attendees (
    meeting_id  UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id),
    checked_in  BOOLEAN NOT NULL DEFAULT FALSE,
    checked_at  TIMESTAMPTZ,
    PRIMARY KEY (meeting_id, user_id)
);
