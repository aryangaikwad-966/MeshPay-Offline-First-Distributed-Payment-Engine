package com.demo.meshpay.saga.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the state of a Saga
 * Persisted to database for microservices architecture
 */
@Entity
@Table(name = "saga_state", indexes = {
    @Index(name = "idx_saga_id", columnList = "sagaId"),
    @Index(name = "idx_packet_hash", columnList = "packetHash"),
    @Index(name = "idx_state", columnList = "state")
}, uniqueConstraints = @UniqueConstraint(name = "uk_saga_packet_hash", columnNames = "packet_hash"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sagaId;

    @Column(nullable = false)
    private String packetHash;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    @Column
    private String currentStep;

    @Column
    private String failureReason;

    @Column(name = "packet_id", length = 80)
    private String packetId;

    @Column(name = "sender_vpa", length = 160)
    private String senderVpa;

    @Column(name = "receiver_vpa", length = 160)
    private String receiverVpa;

    @Column(precision = 19, scale = 2)
    private java.math.BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(length = 128)
    private String nonce;

    private Long signedAt;

    @Column
    private String aggregateId;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.Instant.now();
        updatedAt = java.time.Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.Instant.now();
    }
}
