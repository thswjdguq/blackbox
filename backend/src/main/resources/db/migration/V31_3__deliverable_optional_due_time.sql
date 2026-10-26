-- Local expo branch only. Coordinate migration numbering before shared/main rollout.
ALTER TABLE deliverables ADD COLUMN due_time TIME(0) WITHOUT TIME ZONE;
