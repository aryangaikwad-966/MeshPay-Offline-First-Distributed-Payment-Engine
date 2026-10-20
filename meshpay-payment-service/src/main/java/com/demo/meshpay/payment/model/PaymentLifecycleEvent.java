package com.demo.meshpay.payment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_lifecycle_events", indexes = @Index(name = "idx_payment_history_hash_time", columnList = "packet_hash,occurred_at"))
public class PaymentLifecycleEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "packet_hash", nullable = false, length = 64) private String packetHash;
    @Column(name = "from_state", length = 40) private String fromState;
    @Column(name = "to_state", nullable = false, length = 40) private String toState;
    @Column(length = 512) private String reason;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt = Instant.now();
    protected PaymentLifecycleEvent() {}
    public PaymentLifecycleEvent(String packetHash, String fromState, String toState, String reason) {
        this.packetHash = packetHash; this.fromState = fromState; this.toState = toState; this.reason = reason;
    }
    public Long getId() { return id; }
    public String getPacketHash() { return packetHash; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
