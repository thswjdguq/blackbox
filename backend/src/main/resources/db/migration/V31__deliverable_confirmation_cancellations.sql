-- K-20 0.3 확정 취소. 취소한 확정은 deliverable_confirmations에서 이 표로 옮긴다.
-- 확정했던 회차·사람·시각을 그대로 남기고 취소한 사람·시각·사유를 더한다. 이 표의 줄은 고치거나 지우지 않는다.
-- V26~V30은 시연 브랜치의 마이그레이션을 main으로 옮길 때 쓴다. 이 파일은 그 통합 뒤에 병합한다.

CREATE TABLE deliverable_confirmation_cancellations (
    id             UUID PRIMARY KEY,
    deliverable_id UUID NOT NULL,
    project_id     UUID NOT NULL,
    -- 한 회차의 확정은 한 번만 취소된다. 취소한 회차로는 다시 확정할 수 없다(K-20 2장).
    review_id      UUID NOT NULL UNIQUE,
    confirmed_by   UUID NOT NULL REFERENCES users(id),
    confirmed_at   TIMESTAMPTZ NOT NULL,
    cancelled_by   UUID NOT NULL REFERENCES users(id),
    cancelled_at   TIMESTAMPTZ NOT NULL,
    reason         VARCHAR(1000) NOT NULL,
    -- 확정과 같이, 회차가 같은 제출물·같은 프로젝트의 것임을 복합 외래키로 보장하고 연쇄 삭제를 두지 않는다.
    CONSTRAINT fk_cancellation_review FOREIGN KEY (review_id, deliverable_id, project_id)
        REFERENCES review_rounds (id, deliverable_id, project_id)
);

-- 제출물 목록의 상태가 프로젝트의 취소한 회차를 한 번에 읽는다.
CREATE INDEX idx_confirmation_cancellations_project ON deliverable_confirmation_cancellations (project_id);
