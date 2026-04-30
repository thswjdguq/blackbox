-- 체크인 날짜별 독립 처리를 위한 컬럼 추가
ALTER TABLE meeting_attendees
    ADD COLUMN IF NOT EXISTS checked_in_date DATE;

-- 기존 체크인된 레코드에 오늘 날짜 기본 설정 (마이그레이션 안정성)
UPDATE meeting_attendees
SET checked_in_date = CURRENT_DATE
WHERE checked_in = true AND checked_in_date IS NULL;
