package com.demo.meshpay.saga.processor;

import com.demo.meshpay.saga.model.SagaCommandOutbox;
import com.demo.meshpay.saga.repository.SagaCommandOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaOutboxProcessor {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaCommandOutboxRepository outbox;
    private final MeterRegistry metrics;

    @PostConstruct
    void registerMetrics() {
        metrics.gauge("meshpay.saga.outbox.pending", outbox, r -> r.countByState("PENDING"));
        metrics.gauge("meshpay.saga.outbox.dead", outbox, r -> r.countByState("DEAD"));
    }

    /**
     * Row locks serialize competing publishers. A crash before commit leaves the row pending;
     * a crash after Kafka acknowledgment but before commit can redeliver, so consumers must dedupe.
     */
    @Scheduled(fixedDelayString = "${meshpay.saga.outbox.poll-interval:1000}")
    @Transactional
    public void publishPendingCommands() {
        for (SagaCommandOutbox command : outbox.lockReady(Instant.now(), PageRequest.of(0, 50))) {
            try {
                kafkaTemplate.send(command.getTopic(), command.getSagaId(), command.getPayload())
                        .get(10, TimeUnit.SECONDS);
                command.published();
                metrics.counter("meshpay.saga.outbox.published", "topic", command.getTopic()).increment();
            } catch (Exception e) {
                command.failed(e);
                metrics.counter("meshpay.saga.outbox.publish.errors", "topic", command.getTopic()).increment();
                log.warn("Saga command publication failed: commandId={}, sagaId={}, state={}, attempts={}",
                        command.getCommandId(), command.getSagaId(), command.getState(), command.getAttempts());
            }
        }
    }
}
