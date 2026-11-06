-- Saga stuck-state recovery bookkeeping.
ALTER TABLE saga_state ADD COLUMN IF NOT EXISTS recovery_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE saga_state ADD COLUMN IF NOT EXISTS last_recovery_at TIMESTAMPTZ;
