-- Device-managed signing keys: customers register a device-generated RSA
-- public key so packets can be verified without static env configuration.
-- Keys are revoked, never deleted, so audit history is preserved.

CREATE TABLE IF NOT EXISTS device_keys (
    id            UUID PRIMARY KEY,
    vpa           VARCHAR(128) NOT NULL,
    device_id     VARCHAR(64)  NOT NULL,
    public_key    TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    registered_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at    TIMESTAMPTZ,
    CONSTRAINT uq_device_keys_vpa_device UNIQUE (vpa, device_id),
    CONSTRAINT ck_device_keys_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);
CREATE INDEX IF NOT EXISTS idx_device_keys_vpa_status ON device_keys(vpa, status);
