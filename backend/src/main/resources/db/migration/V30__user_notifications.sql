-- K-54 개인 업무 알림. 엑스포 임시 배정(B-31). 2026-10-14 확인한 최고 번호 V29 다음, V23은 #102 예약으로 비워 둔다.
-- 위험 경보(alerts)는 읽음이 팀 공용이라 재사용하지 않는다. 알림은 받는 사람마다 한 행이며 읽음도 그 행에만 남는다.
CREATE TABLE user_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id),
    actor_id UUID NOT NULL REFERENCES users(id),
    actor_name VARCHAR(100) NOT NULL,
    type VARCHAR(40) NOT NULL CHECK (type IN ('TASK_FILE_UPLOADED', 'TASK_COMPLETION_REQUESTED',
        'TASK_REVISION_REQUESTED', 'TASK_OPINION_REQUESTED', 'TASK_OPINION_REPLIED')),
    event_key VARCHAR(200) NOT NULL,
    -- 업무·대화가 삭제돼도 알림은 남기고 targetAvailable=false로 응답한다. 그래서 FK를 두지 않는다.
    task_id UUID NOT NULL,
    task_title VARCHAR(255) NOT NULL,
    message_id UUID,
    -- 파일 버전은 삭제가 막힌 불변 기록이므로 실제 버전 행을 가리킨다. 다른 프로젝트 파일은 연결할 수 없다.
    file_id UUID,
    file_name VARCHAR(255),
    file_version INT,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_notification_event_recipient UNIQUE (event_key, recipient_id),
    CONSTRAINT notification_not_self CHECK (recipient_id <> actor_id),
    CONSTRAINT fk_notification_file FOREIGN KEY (file_id, project_id) REFERENCES file_vault(id, project_id),
    CONSTRAINT notification_file_shape CHECK ((file_id IS NULL) = (file_name IS NULL) AND (file_id IS NULL) = (file_version IS NULL))
);
CREATE INDEX idx_notifications_feed ON user_notifications(recipient_id, project_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread ON user_notifications(recipient_id, project_id) WHERE read_at IS NULL;
