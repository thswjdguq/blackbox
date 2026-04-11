-- V11: contribution_scores 에 참여 여부 필드 추가
-- 기존 숫자 점수 컬럼은 하위호환 유지를 위해 삭제하지 않음

ALTER TABLE contribution_scores
    ADD COLUMN IF NOT EXISTS task_participated     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS meeting_participated  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS file_participated     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS action_participated   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS participation_level   VARCHAR(10) NOT NULL DEFAULT 'NONE';
