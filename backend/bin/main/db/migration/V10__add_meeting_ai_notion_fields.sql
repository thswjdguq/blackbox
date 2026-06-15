-- V10: 회의록 AI 요약 및 Notion 동기화 필드 추가
ALTER TABLE meetings
    ADD COLUMN ai_summary       TEXT,
    ADD COLUMN notion_page_id   VARCHAR(255),
    ADD COLUMN notion_synced_at TIMESTAMPTZ;
