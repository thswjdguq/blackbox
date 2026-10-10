-- K-10 검토 회차. 회차는 열 때 고른 파일 버전 하나를 가리키고, 파일 없는 중간 검토면 file_id가 NULL이다.
-- 제출물 상태 열은 만들지 않는다. 상태는 회차에서 계산한다(K-10 2장).

-- 회차의 파일이 같은 프로젝트의 파일임을 복합 외래키로 보장하려면 참조되는 쪽에 유일 제약이 필요하다.
-- 제약 추가는 기존 행을 바꾸지 않으므로 수정 차단 트리거(vault_immutable)에 걸리지 않는다.
ALTER TABLE file_vault ADD CONSTRAINT uq_file_vault_id_project UNIQUE (id, project_id);

CREATE TABLE review_rounds (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL,
    deliverable_id UUID NOT NULL,
    round_no       INTEGER NOT NULL,
    file_id        UUID,
    opened_by      UUID NOT NULL REFERENCES users(id),
    opened_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decision       VARCHAR(20),
    decided_by     UUID REFERENCES users(id),
    decided_at     TIMESTAMPTZ,
    -- 두 외래키가 같은 project_id를 쓰므로 회차의 제출물과 파일은 항상 같은 프로젝트다.
    -- 연쇄 삭제를 두지 않는다. 회차가 있는 제출물은 삭제가 막힌다(K-10 4장).
    CONSTRAINT fk_review_round_deliverable FOREIGN KEY (deliverable_id, project_id) REFERENCES deliverables(id, project_id),
    CONSTRAINT fk_review_round_file FOREIGN KEY (file_id, project_id) REFERENCES file_vault(id, project_id),
    CONSTRAINT uq_review_round_no UNIQUE (deliverable_id, round_no),
    -- K-11 코멘트가 (회차, 제출물, 프로젝트) 소속을 복합 외래키로 참조한다.
    CONSTRAINT uq_review_round_scope UNIQUE (id, deliverable_id, project_id),
    CONSTRAINT ck_review_round_no CHECK (round_no >= 1),
    CONSTRAINT ck_review_round_decision CHECK (decision IN ('APPROVED', 'CHANGES_REQUESTED')),
    -- 결정, 결정자, 결정 시각은 함께 있거나 함께 없다.
    CONSTRAINT ck_review_round_decided CHECK ((decision IS NULL) = (decided_by IS NULL) AND (decision IS NULL) = (decided_at IS NULL))
);
