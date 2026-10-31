-- K-54 v0.3(C-56): 업무 배정 개인 알림 추가. V30은 이미 적용된 DB가 있어 수정하지 않고 type CHECK만 교체한다.
-- 2026-10-15 확인: 모든 브랜치·worktree·teamplay-ui 작업 트리에서 V30 다음 번호 미사용.
ALTER TABLE user_notifications DROP CONSTRAINT user_notifications_type_check;
ALTER TABLE user_notifications ADD CONSTRAINT user_notifications_type_check CHECK (type IN ('TASK_FILE_UPLOADED',
    'TASK_COMPLETION_REQUESTED', 'TASK_REVISION_REQUESTED', 'TASK_OPINION_REQUESTED', 'TASK_OPINION_REPLIED',
    'TASK_ASSIGNED'));
