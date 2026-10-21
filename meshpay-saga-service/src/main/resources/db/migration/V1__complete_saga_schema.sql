-- Canonical PostgreSQL schema for persisted Saga state, history, deduplication,
-- and the transactional command outbox.
CREATE TABLE IF NOT EXISTS saga_state (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(255) NOT NULL UNIQUE,
    packet_hash VARCHAR(255) NOT NULL UNIQUE,
    state VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    current_step VARCHAR(255),
    failure_reason VARCHAR(255),
    packet_id VARCHAR(80),
    sender_vpa VARCHAR(160),
    receiver_vpa VARCHAR(160),
    amount NUMERIC(19,2),
    currency VARCHAR(3),
    nonce VARCHAR(128),
    signed_at BIGINT,
    aggregate_id VARCHAR(255),
    CONSTRAINT uk_saga_packet_hash UNIQUE (packet_hash)
);
CREATE INDEX IF NOT EXISTS idx_saga_id ON saga_state(saga_id);
CREATE INDEX IF NOT EXISTS idx_packet_hash ON saga_state(packet_hash);
CREATE INDEX IF NOT EXISTS idx_state ON saga_state(state);

CREATE TABLE IF NOT EXISTS saga_transitions (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(80) NOT NULL,
    from_state VARCHAR(80),
    to_state VARCHAR(80) NOT NULL,
    reason VARCHAR(512),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_saga_transition_saga ON saga_transitions(saga_id, changed_at);

CREATE TABLE IF NOT EXISTS processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_event_id UNIQUE (event_id)
);

CREATE TABLE IF NOT EXISTS saga_command_outbox (
    command_id VARCHAR(36) PRIMARY KEY,
    saga_id VARCHAR(80) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_saga_outbox_ready ON saga_command_outbox(state, next_attempt_at, created_at);
