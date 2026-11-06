-- Financial correctness: immutable ledger entries, full refunds, outbox replay
-- audit fields, expanded payment status set, and seeded demo accounts.

-- Immutable ledger: every settled payment writes exactly one DEBIT (sender)
-- and one CREDIT (receiver) entry; refunds write compensating REFUND entries.
-- Entries are never updated or deleted.
CREATE TABLE IF NOT EXISTS ledger_entries (
    id           BIGSERIAL PRIMARY KEY,
    packet_hash  VARCHAR(64)  NOT NULL,
    account_vpa  VARCHAR(255) NOT NULL,
    direction    VARCHAR(6)   NOT NULL,
    kind         VARCHAR(16)  NOT NULL,
    amount       NUMERIC(19,2) NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_kind CHECK (kind IN ('SETTLEMENT', 'REFUND')),
    CONSTRAINT ck_ledger_amount CHECK (amount > 0),
    -- Exactly-once per leg: a payment can never post the same entry twice.
    CONSTRAINT uk_ledger_entry UNIQUE (packet_hash, account_vpa, direction, kind)
);
CREATE INDEX IF NOT EXISTS idx_ledger_account ON ledger_entries(account_vpa);
CREATE INDEX IF NOT EXISTS idx_ledger_packet ON ledger_entries(packet_hash);

-- Full-refund records. The unique packet_hash makes refund idempotent.
CREATE TABLE IF NOT EXISTS refunds (
    id            BIGSERIAL PRIMARY KEY,
    packet_hash   VARCHAR(64)  NOT NULL,
    transaction_id BIGINT      NOT NULL,
    amount        NUMERIC(19,2) NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    reason        VARCHAR(500),
    requested_by  VARCHAR(255),
    status        VARCHAR(16)  NOT NULL DEFAULT 'COMPLETED',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refund_packet_hash UNIQUE (packet_hash),
    CONSTRAINT ck_refund_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_refunds_transaction ON refunds(transaction_id);

-- Refund metadata on the payment record itself.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMPTZ;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(500);

-- Allow the terminal REFUNDED status.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('SYNCHRONIZED', 'VALIDATED', 'SETTLEMENT_REQUESTED', 'SETTLED', 'REJECTED', 'REFUNDED'));

-- Audit fields for dead-letter replay operations.
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS replay_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS last_replayed_by VARCHAR(255);
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS last_replayed_at TIMESTAMPTZ;

-- Seeded demo accounts so settlement has funded counterparties. Balances are
-- simulated; these are local-development fixtures, not real money.
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES
    ('alice@meshpay.local',     'Alice Demo',     50000.00, 0),
    ('recipient@meshpay.local', 'Recipient Demo',     0.00, 0),
    ('test-sender@meshpay.local', 'Test Sender',   50000.00, 0)
ON CONFLICT (vpa) DO NOTHING;
