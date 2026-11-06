-- Seed an account for the trusted demo sender used by local .env configurations
-- (bob@syncpay.local). Balances are simulated fixtures, not real money.
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES
    ('bob@syncpay.local', 'Bob Demo', 50000.00, 0)
ON CONFLICT (vpa) DO NOTHING;
