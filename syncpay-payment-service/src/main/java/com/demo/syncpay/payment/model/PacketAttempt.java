package com.demo.meshpay.payment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "packet_attempts", indexes = {
        @Index(name = "idx_packet_attempt_outcome", columnList = "outcome"),
        @Index(name = "idx_packet_attempt_hash", columnList = "packetHash")
})
public class PacketAttempt {
    public enum Outcome { ACCEPTED, REJECTED, DUPLICATE, IN_PROGRESS }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 64) private String packetHash;
    @Column(length = 160) private String senderVpa;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private Outcome outcome;
    @Column(length = 512) private String reason;
    @Column(nullable = false, updatable = false) private Instant attemptedAt = Instant.now();

    protected PacketAttempt() {}
    public PacketAttempt(String hash, String sender, Outcome outcome, String reason) {
        this.packetHash = hash; this.senderVpa = sender; this.outcome = outcome; this.reason = reason;
    }
    public Long getId() { return id; }
    public String getPacketHash() { return packetHash; }
    public String getSenderVpa() { return senderVpa; }
    public Outcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
