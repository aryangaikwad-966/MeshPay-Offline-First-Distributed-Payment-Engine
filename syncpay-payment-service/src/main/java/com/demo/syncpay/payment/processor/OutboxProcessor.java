package com.demo.meshpay.payment.processor;

import com.demo.meshpay.payment.model.Outbox;
import com.demo.meshpay.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * Background processor for Transactional Outbox pattern
 * Periodically processes unprocessed outbox events and publishes them to Kafka
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "meshpay.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${meshpay.outbox.max-retries:10}")
    private int maxRetries;

    @PostConstruct
    void registerOutboxGauges() {
        meterRegistry.gauge("meshpay.outbox.pending", outboxRepository, OutboxRepository::countByProcessedFalse);
        meterRegistry.gauge("meshpay.outbox.deadletter", outboxRepository, OutboxRepository::countByDeadLetterTrue);
        meterRegistry.gauge("meshpay.outbox.oldest.pending.seconds", outboxRepository, repository ->
                repository.findFirstByProcessedFalseOrderByCreatedAtAsc()
                        .map(entry -> Math.max(0, Instant.now().getEpochSecond() - entry.getCreatedAt().getEpochSecond()))
                        .orElse(0L));
    }

    /**
     * Process outbox events every 5 seconds
     * Uses pessimistic locking to prevent concurrent processing
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        try {
            // Fetch unprocessed events with pessimistic locking
            List<Outbox> unprocessedEvents = outboxRepository.findUnprocessedEventsForProcessing(50);

            if (unprocessedEvents.isEmpty()) {
                return;
            }

            log.info("Processing {} outbox events", unprocessedEvents.size());

            for (Outbox outbox : unprocessedEvents) {
                try {
                    // Publish event to Kafka
                    boolean deadLetter = outbox.getRetryCount() >= maxRetries;
                    String topic = deadLetter ? outbox.getTopic() + ".DLT" : outbox.getTopic();
                    kafkaTemplate.send(topic, outbox.getPartitionKey(), outbox.getPayload())
                            .get(10, TimeUnit.SECONDS);

                    // Mark as processed
                    outbox.setProcessed(true);
                    outbox.setProcessedAt(Instant.now());
                    outbox.setDeadLetter(deadLetter);
                    outboxRepository.save(outbox);

                    if (deadLetter) meterRegistry.counter("meshpay.outbox.deadletter.published").increment();

                    log.debug("Processed outbox event: eventId={}, topic={}", 
                        outbox.getEventId(), outbox.getTopic());

                } catch (Exception e) {
                    log.error("Failed to process outbox event: eventId={}", outbox.getEventId(), e);
                    
                    // Increment retry count
                    int retries = outbox.getRetryCount() + 1;
                    outbox.setRetryCount(retries);
                    String error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                    outbox.setErrorMessage(error.substring(0, Math.min(error.length(), 1000)));
                    outbox.setNextAttemptAt(Instant.now().plusSeconds(Math.min(300, 1L << Math.min(retries, 8))));
                    outboxRepository.save(outbox);
                    meterRegistry.counter("meshpay.outbox.publish.failure").increment();
                    log.warn("Outbox event publication failed; retained for retry: eventId={}, retryCount={}",
                            outbox.getEventId(), retries);
                }
            }

            log.info("Completed processing outbox events");

        } catch (Exception e) {
            log.error("Error in outbox processor", e);
        }
    }

    /**
     * Clean up processed events older than 24 hours
     * Runs every hour
     */
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanupProcessedEvents() {
        try {
            Instant cutoffTime = Instant.now().minusSeconds(30L * 86400);
            int deletedCount = outboxRepository.deleteProcessedEventsBefore(cutoffTime);
            
            if (deletedCount > 0) {
                log.info("Cleaned up {} processed outbox events older than 24 hours", deletedCount);
            }

        } catch (Exception e) {
            log.error("Error cleaning up processed events", e);
        }
    }
}
