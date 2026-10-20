-- Older MeshPay schema versions made settled_at NOT NULL. A synchronized payment
-- must remain unsettled until the settlement workflow actually completes.
ALTER TABLE IF EXISTS transactions ALTER COLUMN settled_at DROP NOT NULL;
