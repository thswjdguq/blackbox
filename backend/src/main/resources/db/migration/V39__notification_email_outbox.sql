-- K-41 4장 이메일 알림 발송 대기(outbox)와 검토 요청 알림 종류. 번호는 K-41 합의에서 A가 예약한 V39다.
-- 업무·앱 알림과 같은 트랜잭션에 대기 행을 남기고, worker가 커밋 후 따로 SMTP로 보낸다(메일 실패가 업무를 되돌리지 않는다).

-- 1. 앱 알림 종류에 최종 제출물 새 회차의 검토 요청을 더한다. 대상 모양(notification_target_shape)은
--    REVIEW_% 규칙(deliverable_id·review_id 필수, task_id 없음)을 그대로 따르므로 바꾸지 않는다
ALTER TABLE user_notifications DROP CONSTRAINT user_notifications_type_check;
ALTER TABLE user_notifications ADD CONSTRAINT user_notifications_type_check CHECK (type IN (
 'TASK_FILE_UPLOADED','TASK_COMPLETION_REQUESTED','TASK_REVISION_REQUESTED',
 'TASK_OPINION_REQUESTED','TASK_OPINION_REPLIED','TASK_ASSIGNED','TASK_COMMENT_POSTED',
 'REVIEW_FEEDBACK_RECEIVED','REVIEW_FEEDBACK_RESOLVED','REVIEW_FEEDBACK_REOPENED','REVIEW_VERSION_POSTED',
 'REVIEW_REQUESTED'));

-- 2. 발송 대기. 알림 한 건(받는 사람마다 한 행)에 대기 한 행이며, id가 재시도 때 같은 Message-ID를 만드는 delivery id다.
--    수신 주소는 저장하지 않고 발송 직전에 사용자에서 읽는다(주소 변경·탈퇴를 그때 반영한다)
CREATE TABLE notification_email_outbox (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    recipient_id   UUID NOT NULL REFERENCES users(id),
    actor_id       UUID NOT NULL REFERENCES users(id),
    actor_name     VARCHAR(100) NOT NULL,
    event_type     VARCHAR(40) NOT NULL CHECK (event_type IN ('TASK_COMPLETION_REQUESTED', 'REVIEW_REQUESTED')),
    event_key      VARCHAR(200) NOT NULL,
    -- 대상이 삭제돼도 대기는 남기고 worker가 SKIPPED로 끝낸다. 그래서 앱 알림처럼 FK를 두지 않는다
    task_id        UUID,
    deliverable_id UUID,
    review_id      UUID,
    target_title   VARCHAR(255) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'SKIPPED')),
    attempt_count  INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    -- 이 시각 이후에 보낸다. 재시도 지연은 이 값을 뒤로 미룬다
    available_at   TIMESTAMPTZ NOT NULL,
    -- worker가 가져간 시각(lease 시작). 결과를 저장할 때 id와 함께 대조해, lease가 끝난 worker가 새 결과를 덮지 않게 한다
    claimed_at     TIMESTAMPTZ,
    sent_at        TIMESTAMPTZ,
    -- 분류 코드만 둔다(예: TEMPORARY_FAILURE). 원본 SMTP 예외·주소·본문은 남기지 않는다
    error_code     VARCHAR(40),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    -- 앱 알림과 같은 키다. 같은 사건을 두 번 예약하지 않는다
    CONSTRAINT uq_email_outbox_event_recipient UNIQUE (event_key, recipient_id),
    CONSTRAINT email_outbox_not_self CHECK (recipient_id <> actor_id),
    CONSTRAINT email_outbox_target_shape CHECK (
        (event_type = 'TASK_COMPLETION_REQUESTED' AND task_id IS NOT NULL AND deliverable_id IS NULL AND review_id IS NULL)
        OR (event_type = 'REVIEW_REQUESTED' AND task_id IS NULL AND deliverable_id IS NOT NULL AND review_id IS NOT NULL)),
    CONSTRAINT email_outbox_processing_claimed CHECK (status <> 'PROCESSING' OR claimed_at IS NOT NULL),
    CONSTRAINT email_outbox_sent_at_shape CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

-- worker가 가져갈 대기: 보낼 시각이 된 PENDING
CREATE INDEX idx_email_outbox_due ON notification_email_outbox (available_at) WHERE status = 'PENDING';
-- lease가 끝난 PROCESSING 회수(프로세스가 보내는 중에 내려간 경우)
CREATE INDEX idx_email_outbox_claimed ON notification_email_outbox (claimed_at) WHERE status = 'PROCESSING';

CREATE TRIGGER trg_notification_email_outbox_updated_at
    BEFORE UPDATE ON notification_email_outbox
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
