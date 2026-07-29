CREATE TABLE notification_match_tracking (
    match_id BIGINT PRIMARY KEY,
    terminal BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
