CREATE TABLE notification_store_metadata (
    singleton_id INT PRIMARY KEY CHECK (singleton_id = 1),
    target_mode VARCHAR(32) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    verifier_challenge BINARY(32) NOT NULL,
    verifier BINARY(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notification_targets (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    target_mode VARCHAR(32) NOT NULL,
    lookup_digest BINARY(32) NOT NULL,
    lookup_key_id VARCHAR(64) NOT NULL,
    registration_value VARCHAR(4096),
    sendable BOOLEAN NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    accepted_revision BIGINT NOT NULL,
    operation_hash BINARY(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(provider, target_mode, lookup_digest)
);

CREATE TABLE notification_subscriptions (
    id UUID PRIMARY KEY,
    target_id UUID NOT NULL REFERENCES notification_targets(id),
    match_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(target_id, match_id)
);

CREATE TABLE notification_observations (
    id UUID PRIMARY KEY,
    match_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_result VARCHAR(32) NOT NULL,
    UNIQUE(match_id, observed_at)
);

CREATE TABLE notification_delivery_intents (
    id UUID PRIMARY KEY,
    target_id UUID NOT NULL REFERENCES notification_targets(id),
    match_id BIGINT NOT NULL,
    event_type VARCHAR(8) NOT NULL,
    state VARCHAR(32) NOT NULL,
    application_attempt_count INT NOT NULL DEFAULT 0,
    claim_token UUID,
    claimed_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    call_started_at TIMESTAMP WITH TIME ZONE,
    retry_decision_at TIMESTAMP WITH TIME ZONE,
    retry_delay_millis BIGINT,
    due_at TIMESTAMP WITH TIME ZONE,
    terminal_reason VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(target_id, match_id, event_type)
);

CREATE TABLE notification_audit_events (
    id UUID PRIMARY KEY,
    target_id UUID REFERENCES notification_targets(id),
    category VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX notification_targets_digest_idx ON notification_targets(provider, target_mode, lookup_digest);
CREATE INDEX notification_intents_due_idx ON notification_delivery_intents(state, due_at);
