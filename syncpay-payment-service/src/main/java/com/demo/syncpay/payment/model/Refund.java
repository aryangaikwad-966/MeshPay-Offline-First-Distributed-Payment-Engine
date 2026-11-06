package com.demo.syncpay.payment.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Record of a completed full refund. The unique packet_hash constraint makes
 * refund requests idempotent — a second request returns the existing record
 * instead of double-refunding.
 */
@Entity
@Table(name = "refunds",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_packet_hash", columnNames = "packet_hash"))
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "packet_hash", nullable = false, length = 64)
    private String packetHash;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String reason;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(nullable = false, length = 16)
    private String status = "COMPLETED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Refund() {}

    public Refund(String packetHash, Long transactionId, BigDecimal amount, String currency,
                  String reason, String requestedBy) {
        this.packetHash = packetHash;
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    public Long getId() { return id; }
    public String getPacketHash() { return packetHash; }
    public Long getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReason() { return reason; }
    public String getRequestedBy() { return requestedBy; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
