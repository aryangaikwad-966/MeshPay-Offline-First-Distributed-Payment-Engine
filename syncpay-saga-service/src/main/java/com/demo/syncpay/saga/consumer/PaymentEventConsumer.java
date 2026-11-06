package com.demo.meshpay.saga.consumer;

import com.demo.meshpay.saga.events.PaymentFailedEvent;
import com.demo.meshpay.saga.events.PaymentSettlementRequestedEvent;
import com.demo.meshpay.saga.events.PaymentSettledEvent;
import com.demo.meshpay.saga.events.PaymentValidatedEvent;
import com.demo.meshpay.saga.model.ProcessedEvent;
import com.demo.meshpay.saga.repository.ProcessedEventRepository;
import com.demo.meshpay.saga.service.PaymentSagaOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {
    private final PaymentSagaOrchestrator orchestrator;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "payment-packet-synchronized", groupId = "saga-service-group")
    @Transactional
    public void handlePacketSynchronized(String message) throws Exception {
        JsonNode root = mapper.readTree(message);
        String eventId = eventId(root);
        if (alreadyProcessed(eventId)) return;
        JsonNode p = payload(root);
        orchestrator.startSynchronizedPayment(p.path("packetId").asText(), p.path("packetHash").asText(),
                p.path("senderVpa").asText(), p.path("receiverVpa").asText(), p.path("amount").decimalValue(),
                p.path("currency").asText(), p.path("senderNonce").asText(), p.path("signedAt").asLong());
        markProcessed(eventId, "payment-packet-synchronized");
        log.info("Synchronized payment event accepted by Saga: eventId={}", eventId);
    }

    @KafkaListener(topics = "payment-validated", groupId = "saga-service-group")
    @Transactional
    public void handlePaymentValidated(String message) throws Exception {
        JsonNode root = mapper.readTree(message);
        String eventId = eventId(root);
        if (alreadyProcessed(eventId)) return;
        PaymentValidatedEvent event = mapper.treeToValue(payload(root), PaymentValidatedEvent.class);
        String sagaId = requireSaga(event.getPacketHash());
        orchestrator.requestSettlementSaga(sagaId, event.getPacketId(), event.getPacketHash(), event.getSenderVpa(),
                event.getReceiverVpa(), event.getAmount(), event.getNonce(), event.getSignedAt());
        markProcessed(eventId, "payment-validated");
        log.info("Payment validation event advanced Saga: sagaId={}, eventId={}", sagaId, eventId);
    }

    @KafkaListener(topics = "payment-settlement-requested", groupId = "saga-service-group")
    @Transactional
    public void handlePaymentSettlementRequested(String message) throws Exception {
        JsonNode root = mapper.readTree(message);
        String eventId = eventId(root);
        if (alreadyProcessed(eventId)) return;
        PaymentSettlementRequestedEvent event = mapper.treeToValue(payload(root), PaymentSettlementRequestedEvent.class);
        String sagaId = requireSaga(event.getPacketHash());
        orchestrator.completePaymentSaga(sagaId, event.getPacketId(), event.getPacketHash(), event.getSenderVpa(),
                event.getReceiverVpa(), event.getAmount());
        markProcessed(eventId, "payment-settlement-requested");
        log.info("Settlement request event advanced Saga: sagaId={}, eventId={}", sagaId, eventId);
    }

    @KafkaListener(topics = "payment-settled", groupId = "saga-service-group")
    @Transactional
    public void handlePaymentSettled(String message) throws Exception {
        JsonNode root = mapper.readTree(message);
        String eventId = eventId(root);
        if (alreadyProcessed(eventId)) return;
        PaymentSettledEvent event = mapper.treeToValue(payload(root), PaymentSettledEvent.class);
        String sagaId = requireSaga(event.getPacketHash());
        orchestrator.markPaymentCompleted(sagaId);
        markProcessed(eventId, "payment-settled");
        log.info("Payment completion event closed Saga: sagaId={}, eventId={}", sagaId, eventId);
    }

    @KafkaListener(topics = "payment-failed", groupId = "saga-service-group")
    @Transactional
    public void handlePaymentFailed(String message) throws Exception {
        JsonNode root = mapper.readTree(message);
        String eventId = eventId(root);
        if (alreadyProcessed(eventId)) return;
        PaymentFailedEvent event = mapper.treeToValue(payload(root), PaymentFailedEvent.class);
        String sagaId = requireSaga(event.getPacketHash());
        orchestrator.compensatePaymentSaga(sagaId, event.getPacketHash(), event.getFailureReason());
        markProcessed(eventId, "payment-failed");
        log.warn("Payment failure triggered Saga compensation: sagaId={}, eventId={}", sagaId, eventId);
    }

    private boolean alreadyProcessed(String eventId) { return processedEvents.existsByEventId(eventId); }
    private void markProcessed(String eventId, String topic) { processedEvents.save(new ProcessedEvent(eventId, topic)); }
    private String requireSaga(String packetHash) {
        String sagaId = orchestrator.findSagaByPacketHash(packetHash);
        if (sagaId == null) throw new IllegalStateException("No Saga exists for payment event");
        return sagaId;
    }
    private String eventId(JsonNode root) {
        String id = root.path("eventId").asText("");
        if (id.isBlank()) throw new IllegalArgumentException("Kafka event has no eventId");
        return id;
    }
    private JsonNode payload(JsonNode root) {
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("Kafka event payload is malformed");
        return payload;
    }
}
