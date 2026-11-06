package com.demo.syncpay.payment.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A signing public key registered by a customer device. The matching private
 * key never leaves the device; revocation disables verification for packets
 * signed by it without deleting the audit record.
 */
@Entity
@Table(name = "device_keys")
public class DeviceKey {

    public enum Status { ACTIVE, REVOKED }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String vpa;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public DeviceKey() {}

    public static DeviceKey register(String vpa, String deviceId, String publicKey) {
        DeviceKey key = new DeviceKey();
        key.id = UUID.randomUUID();
        key.vpa = vpa.toLowerCase();
        key.deviceId = deviceId;
        key.publicKey = publicKey;
        return key;
    }

    public void revoke() {
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
    }

    /** Re-registration replaces key material and reactivates the device. */
    public void rotate(String newPublicKey) {
        this.publicKey = newPublicKey;
        this.status = Status.ACTIVE;
        this.revokedAt = null;
        this.registeredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getVpa() { return vpa; }
    public String getDeviceId() { return deviceId; }
    public String getPublicKey() { return publicKey; }
    public Status getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
