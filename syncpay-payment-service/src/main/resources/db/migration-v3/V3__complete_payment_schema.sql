-- Canonical PostgreSQL schema for the payment service. IF NOT EXISTS keeps this
-- safe for development databases that already contain Hibernate-created tables.
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    packet_hash VARCHAR(64) NOT NULL UNIQUE,
    packet_id VARCHAR(80) NOT NULL UNIQUE,
    sender_vpa VARCHAR(255) NOT NULL,
    receiver_vpa VARCHAR(255) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    synchronized_at TIMESTAMPTZ NOT NULL,
    bridge_node_id VARCHAR(255) NOT NULL,
    hop_count INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT uk_transaction_sender_nonce UNIQUE (sender_vpa, nonce)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_packet_hash ON transactions(packet_hash);

CREATE TABLE IF NOT EXISTS accounts (
    vpa VARCHAR(255) PRIMARY KEY,
    holder_name VARCHAR(255) NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    version BIGINT
);

CREATE TABLE IF NOT EXISTS outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    packet_hash VARCHAR(255),
    payload TEXT NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    correlation_id VARCHAR(255),
    producer VARCHAR(100),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    dead_letter BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    schema_version VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_outbox_processed ON outbox(processed);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox(created_at);

CREATE TABLE IF NOT EXISTS packet_attempts (
    id BIGSERIAL PRIMARY KEY,
    packet_hash VARCHAR(64),
    sender_vpa VARCHAR(160),
    outcome VARCHAR(24) NOT NULL,
    reason VARCHAR(512),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_packet_attempt_outcome ON packet_attempts(outcome);
CREATE INDEX IF NOT EXISTS idx_packet_attempt_hash ON packet_attempts(packet_hash);

CREATE TABLE IF NOT EXISTS payment_lifecycle_events (
    id BIGSERIAL PRIMARY KEY,
    packet_hash VARCHAR(64) NOT NULL,
    from_state VARCHAR(40),
    to_state VARCHAR(40) NOT NULL,
    reason VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payment_history_hash_time ON payment_lifecycle_events(packet_hash, occurred_at);

CREATE TABLE IF NOT EXISTS processed_commands (
    id BIGSERIAL PRIMARY KEY,
    command_id VARCHAR(255) NOT NULL UNIQUE,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_command_id UNIQUE (command_id)
);
