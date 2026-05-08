CREATE TABLE discord_settings (
    project_id         UUID    PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    webhook_url        TEXT,
    notify_task_assign    BOOLEAN NOT NULL DEFAULT TRUE,
    notify_task_done      BOOLEAN NOT NULL DEFAULT TRUE,
    notify_meeting_create BOOLEAN NOT NULL DEFAULT TRUE,
    notify_meeting_reminder BOOLEAN NOT NULL DEFAULT TRUE,
    notify_alert          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
