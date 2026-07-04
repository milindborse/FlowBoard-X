-- Run this against your flowboardx database if Hibernate's ddl-auto=update
-- did not pick up the new Workflow.java fields automatically.

ALTER TABLE workflows
    ADD COLUMN IF NOT EXISTS category VARCHAR(100);

ALTER TABLE workflows
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

-- Optional: backfill status for any pre-existing rows based on whether
-- they already have a published version, so old data isn't stuck as DRAFT.
UPDATE workflows w
SET status = 'PUBLISHED'
WHERE EXISTS (
    SELECT 1 FROM workflow_versions v
    WHERE v.workflow_id = w.id AND v.published = true
) AND w.status = 'DRAFT';