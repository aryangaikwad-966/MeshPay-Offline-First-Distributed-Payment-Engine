-- The original schema constrained status to terminal values. The synchronization
-- workflow needs intermediate, non-settlement states to be durable as well.
ALTER TABLE IF EXISTS transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE IF EXISTS transactions
    ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('SYNCHRONIZED', 'VALIDATED', 'SETTLEMENT_REQUESTED', 'SETTLED', 'REJECTED'));
