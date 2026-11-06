package com.demo.meshpay.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Envelope for all events in the system
 * Provides metadata and schema versioning for event evolution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {

    /**
     * Unique identifier for this event
     */
    private String eventId;

    /**
     * Type of event (e.g., PAYMENT_RECEIVED, PAYMENT_SETTLED)
     */
    private String eventType;

    /**
     * Aggregate root identifier (e.g., packetHash for payment events)
     */
    private String aggregateId;

    /**
     * Packet hash for payment-related events (used for idempotency)
     */
    private String packetHash;

    /**
     * Correlation ID for distributed tracing
     */
    private String correlationId;

    /**
     * When this event was created
     */
    private Instant timestamp;

    /**
     * Service that produced this event
     */
    private String producer;

    /**
     * Schema version for event evolution
     */
    private String schemaVersion;

    /**
     * The actual event payload
     */
    private Object payload;
}
