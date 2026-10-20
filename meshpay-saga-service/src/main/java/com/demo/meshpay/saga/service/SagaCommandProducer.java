package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.events.CompleteSettlementCommand;
import com.demo.meshpay.saga.events.RequestSettlementCommand;
import com.demo.meshpay.saga.events.ValidatePaymentCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * Kafka producer for Saga commands to Payment Service
 * Enables decoupled microservices communication
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaCommandProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String VALIDATE_PAYMENT_COMMAND_TOPIC = "saga-validate-payment-command";
    private static final String REQUEST_SETTLEMENT_COMMAND_TOPIC = "saga-request-settlement-command";
    private static final String COMPLETE_SETTLEMENT_COMMAND_TOPIC = "saga-complete-settlement-command";

    /**
     * Send validate payment command to Payment Service
     */
    public void sendValidatePaymentCommand(ValidatePaymentCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(VALIDATE_PAYMENT_COMMAND_TOPIC, command.getSagaId(), json).get(10, TimeUnit.SECONDS);
            log.info("Sent validate payment command: sagaId={}", command.getSagaId());
        } catch (Exception e) {
            log.error("Failed to send validate payment command: sagaId={}", command.getSagaId(), e);
            throw new RuntimeException("Failed to send validate payment command", e);
        }
    }

    /**
     * Send request settlement command to Payment Service
     */
    public void sendRequestSettlementCommand(RequestSettlementCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(REQUEST_SETTLEMENT_COMMAND_TOPIC, command.getSagaId(), json).get(10, TimeUnit.SECONDS);
            log.info("Sent request settlement command: sagaId={}", command.getSagaId());
        } catch (Exception e) {
            log.error("Failed to send request settlement command: sagaId={}", command.getSagaId(), e);
            throw new RuntimeException("Failed to send request settlement command", e);
        }
    }

    /**
     * Send complete settlement command to Payment Service
     */
    public void sendCompleteSettlementCommand(CompleteSettlementCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(COMPLETE_SETTLEMENT_COMMAND_TOPIC, command.getSagaId(), json).get(10, TimeUnit.SECONDS);
            log.info("Sent complete settlement command: sagaId={}", command.getSagaId());
        } catch (Exception e) {
            log.error("Failed to send complete settlement command: sagaId={}", command.getSagaId(), e);
            throw new RuntimeException("Failed to send complete settlement command", e);
        }
    }
}
