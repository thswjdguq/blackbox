-- K-20 최종 확정과 제출 기록. 기본 키가 제출물 id라서 확정과 제출 기록은 제출물마다 한 줄이다.
-- 제출물 상태 열은 만들지 않는다. 상태는 이 두 표에 줄이 있는지와 회차에서 정한다(K-20 2장).

CREATE TABLE deliverable_confirmations (
    deliverable_id UUID PRIMARY KEY,
    project_id     UUID NOT NULL,
    -- 확정본은 이 회차가 가리키는 파일 버전이다. 파일 버전을 따로 저장하지 않는다(K-20 7장).
    review_id      UUID NOT NULL,
    confirmed_by   UUID NOT NULL REFERENCES users(id),
    confirmed_at   TIMESTAMPTZ NOT NULL,
    -- 회차가 같은 제출물, 같은 프로젝트의 것임을 복합 외래키로 보장한다.
    -- 연쇄 삭제를 두지 않는다. 확정된 제출물과 그 회차는 지울 수 없다.
    CONSTRAINT fk_confirmation_review FOREIGN KEY (review_id, deliverable_id, project_id)
        REFERENCES review_rounds (id, deliverable_id, project_id)
);

-- 프로젝트의 제출물 목록이 확정 여부를 한 번에 읽는다.
CREATE INDEX idx_deliverable_confirmations_project ON deliverable_confirmations (project_id);

CREATE TABLE deliverable_submissions (
    -- 확정된 제출물만 기록할 수 있다.
    deliverable_id UUID PRIMARY KEY REFERENCES deliverable_confirmations (deliverable_id),
    channel        VARCHAR(500),
    submitted_at   TIMESTAMPTZ NOT NULL,
    note           VARCHAR(1000),
    recorded_by    UUID NOT NULL REFERENCES users(id),
    recorded_at    TIMESTAMPTZ NOT NULL
);
