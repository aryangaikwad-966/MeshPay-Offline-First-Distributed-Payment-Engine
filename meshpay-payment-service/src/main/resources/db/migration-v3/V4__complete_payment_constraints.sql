-- Ensure fresh and previously Hibernate-managed databases enforce the same
-- lifecycle and idempotency invariants. Any conflicting legacy rows stop this
-- migration safely; operators must reconcile those rows before retrying.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('SYNCHRONIZED', 'VALIDATED', 'SETTLEMENT_REQUESTED', 'SETTLED', 'REJECTED'));

CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_packet_id ON transactions(packet_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_sender_nonce ON transactions(sender_vpa, nonce);
CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_commands_command_id ON processed_commands(command_id);
