package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.events.EventEnvelope;
import com.demo.meshpay.payment.model.Outbox;
import com.demo.meshpay.payment.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Service for Transactional Outbox Pattern
 * Ensures atomicity between database transaction and event publication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Save an event to the outbox table within the current transaction
     * This should be called within the same transaction as the business operation
     */
    @Transactional
    public Outbox saveEvent(EventEnvelope event, String topic, String partitionKey, String producer) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            
            Outbox outbox = Outbox.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .aggregateId(event.getAggregateId())
                    .packetHash(event.getPacketHash())
                    .payload(payloadJson)
                    .topic(topic)
                    .partitionKey(partitionKey)
                    .correlationId(event.getCorrelationId())
                    .producer(producer)
                    .processed(false)
                    .retryCount(0)
                    .createdAt(Instant.now())
                    .schemaVersion(event.getSchemaVersion())
                    .build();
            
            return outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to save event to outbox: eventId={}", event.getEventId(), e);
            throw new RuntimeException("Failed to save event to outbox", e);
        }
    }

    /**
     * Find unprocessed outbox entries with locking
     * Used by the background processor
     */
    @Transactional
    public List<Outbox> findUnprocessedEntriesWithLock() {
        return outboxRepository.findUnprocessedEntriesWithLock();
    }

    /**
     * Mark an outbox entry as processed
     */
    @Transactional
    public void markAsProcessed(Long id) {
        outboxRepository.markAsProcessed(id, Instant.now());
        log.debug("Marked outbox entry as processed: id={}", id);
    }

    /**
     * Increment retry count and set error message
     */
    @Transactional
    public void incrementRetryCount(Long id, String errorMessage) {
        outboxRepository.incrementRetryCount(id, errorMessage);
        log.debug("Incremented retry count for outbox entry: id={}, error={}", id, errorMessage);
    }

    /**
     * Delete processed outbox entries older than specified time
     * Used for cleanup
     */
    @Transactional
    public void deleteProcessedOlderThan(Instant cutoffTime) {
        outboxRepository.deleteProcessedOlderThan(cutoffTime);
        log.info("Deleted processed outbox entries older than {}", cutoffTime);
    }

    /**
     * Get count of unprocessed entries
     */
    public long getUnprocessedCount() {
        return outboxRepository.countByProcessedFalse();
    }
}
