-- Seed an account for the trusted demo sender used by local .env configurations
-- (bob@meshpay.local). Balances are simulated fixtures, not real money.
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES
    ('bob@meshpay.local', 'Bob Demo', 50000.00, 0)
ON CONFLICT (vpa) DO NOTHING;
