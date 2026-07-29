CREATE TABLE notification_match_tracking (
    match_id BIGINT PRIMARY KEY,
    terminal BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- A V1 database may already contain a terminal observation.  Preserve that
-- evidence when the durable one-way polling latch is introduced so an upgrade
-- cannot make an already completed or cancelled match active again.
INSERT INTO notification_match_tracking (match_id, terminal, updated_at)
SELECT match_id, TRUE, MAX(observed_at)
FROM notification_observations
WHERE source_result = 'SUCCESS'
  AND status IN ('COMPLETED', 'CANCELLED')
GROUP BY match_id;
