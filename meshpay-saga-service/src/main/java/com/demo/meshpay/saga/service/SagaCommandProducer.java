package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.events.CompleteSettlementCommand;
import com.demo.meshpay.saga.events.RequestSettlementCommand;
import com.demo.meshpay.saga.events.ValidatePaymentCommand;
import com.demo.meshpay.saga.model.SagaCommandOutbox;
import com.demo.meshpay.saga.repository.SagaCommandOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stores outbound commands in the Saga database transaction for later Kafka delivery. */
@Service
@RequiredArgsConstructor
public class SagaCommandProducer {
    private static final String VALIDATE = "saga-validate-payment-command";
    private static final String REQUEST_SETTLEMENT = "saga-request-settlement-command";
    private static final String COMPLETE_SETTLEMENT = "saga-complete-settlement-command";
    private final SagaCommandOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    @Transactional
    public void sendValidatePaymentCommand(ValidatePaymentCommand command) {
        enqueue(command.getSagaId(), "VALIDATE", VALIDATE, command);
    }
    @Transactional
    public void sendRequestSettlementCommand(RequestSettlementCommand command) {
        enqueue(command.getSagaId(), "REQUEST_SETTLEMENT", REQUEST_SETTLEMENT, command);
    }
    @Transactional
    public void sendCompleteSettlementCommand(CompleteSettlementCommand command) {
        enqueue(command.getSagaId(), "COMPLETE_SETTLEMENT", COMPLETE_SETTLEMENT, command);
    }

    private void enqueue(String sagaId, String action, String topic, Object command) {
        try {
            String id = UUID.nameUUIDFromBytes((sagaId + ":" + action).getBytes(StandardCharsets.UTF_8)).toString();
            if (!outbox.existsById(id))
                outbox.save(new SagaCommandOutbox(id, sagaId, topic, objectMapper.writeValueAsString(command)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist Saga command in outbox", e);
        }
    }
}
