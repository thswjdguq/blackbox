-- EXPO-06: 사용자 승인으로 엑스포 DB/API 확장. V32는 #133 예약이므로 31.1을 사용한다.
-- 기존 V30/V31 데이터와 체크섬은 보존한다. 이 분기는 V31 기준이며 V32 배포 전 함께 통합해야 한다.
ALTER TABLE user_notifications ALTER COLUMN task_id DROP NOT NULL;
ALTER TABLE user_notifications ADD COLUMN deliverable_id UUID;
ALTER TABLE user_notifications ADD COLUMN review_id UUID;
ALTER TABLE user_notifications ADD COLUMN comment_id UUID;
ALTER TABLE user_notifications DROP CONSTRAINT user_notifications_type_check;
ALTER TABLE user_notifications ADD CONSTRAINT user_notifications_type_check CHECK (type IN (
 'TASK_FILE_UPLOADED','TASK_COMPLETION_REQUESTED','TASK_REVISION_REQUESTED',
 'TASK_OPINION_REQUESTED','TASK_OPINION_REPLIED','TASK_ASSIGNED','TASK_COMMENT_POSTED',
 'REVIEW_FEEDBACK_RECEIVED','REVIEW_FEEDBACK_RESOLVED','REVIEW_FEEDBACK_REOPENED','REVIEW_VERSION_POSTED'));
ALTER TABLE user_notifications ADD CONSTRAINT notification_target_shape CHECK (
 (type LIKE 'TASK_%' AND task_id IS NOT NULL AND deliverable_id IS NULL AND review_id IS NULL AND comment_id IS NULL)
 OR (type LIKE 'REVIEW_%' AND task_id IS NULL AND deliverable_id IS NOT NULL AND review_id IS NOT NULL AND message_id IS NULL));
