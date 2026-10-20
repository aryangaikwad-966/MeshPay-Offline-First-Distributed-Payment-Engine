package com.demo.meshpay.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outbox Pattern Entity
 * Ensures atomicity between database transaction and event publication
 * Events are written to this table within the same transaction as the business data
 * A background job then publishes these events to Kafka and marks them as processed
 */
@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "idx_outbox_processed", columnList = "processed"),
    @Index(name = "idx_outbox_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for this outbox entry
     */
    @Column(nullable = false, unique = true, length = 255)
    private String eventId;

    /**
     * Type of event (e.g., PAYMENT_RECEIVED, PAYMENT_SETTLED)
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * Aggregate root identifier (e.g., packetHash for payment events)
     */
    @Column(nullable = false, length = 255)
    private String aggregateId;

    /**
     * Packet hash for payment-related events (used for idempotency)
     */
    @Column(length = 255)
    private String packetHash;

    /**
     * The event payload as JSON
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    @Lob
    private String payload;

    /**
     * Kafka topic to publish to
     */
    @Column(nullable = false, length = 255)
    private String topic;

    /**
     * Partition key for Kafka message
     */
    @Column(length = 255)
    private String partitionKey;

    /**
     * Correlation ID for distributed tracing
     */
    @Column(length = 255)
    private String correlationId;

    /**
     * Service that created this outbox entry
     */
    @Column(length = 100)
    private String producer;

    /**
     * Whether this event has been successfully published to Kafka
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    /**
     * Number of retry attempts
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deadLetter = false;

    /**
     * Error message if processing failed
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * When this outbox entry was created
     */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * When this outbox entry was processed
     */
    private Instant processedAt;

    @Column(name = "next_attempt_at")
    @Builder.Default
    private Instant nextAttemptAt = Instant.now();

    /**
     * Schema version for event evolution
     */
    @Column(length = 50)
    private String schemaVersion;

    @PrePersist
    protected void initializeDeliveryState() {
        if (createdAt == null) createdAt = Instant.now();
        if (nextAttemptAt == null) nextAttemptAt = Instant.now();
        if (retryCount == null) retryCount = 0;
        if (processed == null) processed = false;
        if (deadLetter == null) deadLetter = false;
    }
}
