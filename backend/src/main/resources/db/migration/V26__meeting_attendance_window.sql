-- UI 시연 브랜치 전용 제안. main 반영 전 A와 번호·계약을 조정한다.
ALTER TABLE meetings ADD COLUMN attendance_grace_minutes INTEGER NOT NULL DEFAULT 10;
ALTER TABLE meetings ADD COLUMN attendance_window_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE meetings ADD CONSTRAINT meeting_attendance_window_valid CHECK (
    attendance_grace_minutes BETWEEN 0 AND 120
    AND attendance_window_minutes BETWEEN 1 AND 480
    AND attendance_window_minutes > attendance_grace_minutes
);
ALTER TABLE meeting_attendees ADD COLUMN attendance_status VARCHAR(20);
ALTER TABLE meeting_attendees ADD CONSTRAINT attendance_status_valid CHECK (
    attendance_status IS NULL OR attendance_status IN ('PRESENT', 'LATE', 'ABSENT')
);
-- 기존 체크인 기록은 보존한다. 과거 생성자의 자동 체크인을 실제 참석으로 단정하지 않는다.
