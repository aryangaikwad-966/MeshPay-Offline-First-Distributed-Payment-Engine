package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.events.CompleteSettlementCommand;
import com.demo.meshpay.saga.events.RequestSettlementCommand;
import com.demo.meshpay.saga.events.ValidatePaymentCommand;
import com.demo.meshpay.saga.metrics.SagaMetrics;
import com.demo.meshpay.saga.model.SagaState;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saga Orchestrator for Payment Settlement
 * Manages distributed transactions with compensation actions
 * Decoupled from Payment Service via Kafka events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {

    private final SagaStateManager sagaStateManager;
    private final SagaMetrics sagaMetrics;
    private final SagaCommandProducer sagaCommandProducer;

    /**
     * Start a new payment saga
     */
    @Transactional
    public String startPaymentSaga(String packetId, String packetHash, String bridgeNodeId, String ciphertext) {
        String sagaId = UUID.randomUUID().toString();
        Timer.Sample timer = sagaMetrics.startSagaExecutionTimer();
        
        log.info("Starting payment saga: sagaId={}, packetHash={}", sagaId, packetHash);
        
        try {
            // Create saga state
            sagaStateManager.createSaga(sagaId, packetHash, "PAYMENT_RECEIVED");
            
            // Send validate payment command to Payment Service
            // In a real system, this would be triggered by receiving a payment event
            // For now, we'll simulate the flow
            
            sagaMetrics.incrementSagaStarted();
            
            log.info("Payment saga started successfully: sagaId={}", sagaId);
            return sagaId;
            
        } catch (Exception e) {
            log.error("Failed to start payment saga: sagaId={}", sagaId, e);
            sagaStateManager.updateSagaState(sagaId, "SAGA_START_FAILED");
            sagaMetrics.incrementSagaCompensated();
            sagaMetrics.stopSagaExecutionTimer(timer);
            throw new RuntimeException("Failed to start payment saga", e);
        }
    }

    @Transactional
    public String startSynchronizedPayment(String packetId, String packetHash, String senderVpa, String receiverVpa,
            java.math.BigDecimal amount, String currency, String nonce, Long signedAt) {
        String existing = sagaStateManager.findSagaByPacketHash(packetHash);
        if (existing != null) return existing;
        String sagaId = UUID.randomUUID().toString();
        sagaStateManager.createSynchronizedPaymentSaga(sagaId, packetHash, packetId, senderVpa, receiverVpa,
                amount, currency, nonce, signedAt);
        sagaMetrics.incrementSagaStarted();
        validatePaymentSaga(sagaId, packetId, packetHash, senderVpa, receiverVpa, amount, nonce, signedAt);
        return sagaId;
    }

    /**
     * Continue saga with payment validation
     * Sends Kafka command to Payment Service instead of direct call
     */
    @Transactional
    public void validatePaymentSaga(String sagaId, String packetId, String packetHash, 
                                   String senderVpa, String receiverVpa, java.math.BigDecimal amount, 
                                   String nonce, Long signedAt) {
        log.info("Validating payment in saga: sagaId={}, packetHash={}", sagaId, packetHash);

        SagaState existingState = sagaStateManager.getSagaState(sagaId);
        if (existingState != null && "PAYMENT_VALIDATION_COMMAND_QUEUED".equals(existingState.getState())) return;
        
        sagaStateManager.updateSagaState(sagaId, "VALIDATING_PAYMENT");
        
        try {
            // Send Kafka command to Payment Service
            ValidatePaymentCommand command = ValidatePaymentCommand.builder()
                    .sagaId(sagaId)
                    .packetId(packetId)
                    .packetHash(packetHash)
                    .senderVpa(senderVpa)
                    .receiverVpa(receiverVpa)
                    .amount(amount)
                    .nonce(nonce)
                    .signedAt(signedAt)
                    .build();
            sagaCommandProducer.sendValidatePaymentCommand(command);
            sagaStateManager.updateSagaState(sagaId, "PAYMENT_VALIDATION_COMMAND_QUEUED");
            
        } catch (Exception e) {
            log.error("Payment saga failed at VALIDATION: sagaId={}", sagaId, e);
            sagaStateManager.updateSagaState(sagaId, "PAYMENT_VALIDATION_FAILED");
            compensateValidation(sagaId, packetHash);
            throw new RuntimeException("Payment validation failed", e);
        }
    }

    /**
     * Continue saga with settlement request
     * Sends Kafka command to Payment Service instead of direct call
     */
    @Transactional
    public void requestSettlementSaga(String sagaId, String packetId, String packetHash,
                                      String senderVpa, String receiverVpa, java.math.BigDecimal amount,
                                      String nonce, Long signedAt) {
        log.info("Requesting settlement in saga: sagaId={}, packetHash={}", sagaId, packetHash);

        SagaState existingState = sagaStateManager.getSagaState(sagaId);
        if (existingState != null && "SETTLEMENT_REQUEST_COMMAND_QUEUED".equals(existingState.getState())) return;
        
        sagaStateManager.updateSagaState(sagaId, "REQUESTING_SETTLEMENT");
        
        try {
            // Send Kafka command to Payment Service
            RequestSettlementCommand command = RequestSettlementCommand.builder()
                    .sagaId(sagaId)
                    .packetId(packetId)
                    .packetHash(packetHash)
                    .senderVpa(senderVpa)
                    .receiverVpa(receiverVpa)
                    .amount(amount)
                    .nonce(nonce)
                    .signedAt(signedAt)
                    .build();
            sagaCommandProducer.sendRequestSettlementCommand(command);
            sagaStateManager.updateSagaState(sagaId, "SETTLEMENT_REQUEST_COMMAND_QUEUED");
            
        } catch (Exception e) {
            log.error("Payment saga failed at SETTLEMENT_REQUEST: sagaId={}", sagaId, e);
            sagaStateManager.updateSagaState(sagaId, "SETTLEMENT_REQUEST_FAILED");
            compensateSettlementRequest(sagaId, packetHash);
            throw new RuntimeException("Settlement request failed", e);
        }
    }

    /**
     * Complete saga with settlement confirmation
     * Sends Kafka command to Payment Service instead of direct call
     */
    @Transactional
    public void completePaymentSaga(String sagaId, String packetId, String packetHash,
                                    String senderVpa, String receiverVpa, java.math.BigDecimal amount) {
        log.info("Completing payment saga: sagaId={}, packetHash={}", sagaId, packetHash);

        SagaState existingState = sagaStateManager.getSagaState(sagaId);
        if (existingState != null && "SETTLEMENT_COMPLETION_COMMAND_QUEUED".equals(existingState.getState())) return;
        
        sagaStateManager.updateSagaState(sagaId, "COMPLETING_SETTLEMENT");
        
        try {
            // Send Kafka command to Payment Service
            CompleteSettlementCommand command = CompleteSettlementCommand.builder()
                    .sagaId(sagaId)
                    .packetId(packetId)
                    .packetHash(packetHash)
                    .senderVpa(senderVpa)
                    .receiverVpa(receiverVpa)
                    .amount(amount)
                    .build();
            sagaCommandProducer.sendCompleteSettlementCommand(command);
            sagaStateManager.updateSagaState(sagaId, "SETTLEMENT_COMPLETION_COMMAND_QUEUED");
            
        } catch (Exception e) {
            log.error("Payment saga failed at COMPLETION: sagaId={}", sagaId, e);
            sagaStateManager.updateSagaState(sagaId, "SETTLEMENT_COMPLETION_FAILED");
            sagaMetrics.incrementSagaCompensated();
            compensateSettlement(sagaId, packetHash);
            throw new RuntimeException("Settlement completion failed", e);
        }
    }

    /**
     * Compensation actions for validation failure
     */
    private void compensateValidation(String sagaId, String packetHash) {
        log.info("Compensating validation: sagaId={}", sagaId);
        sagaStateManager.updateSagaState(sagaId, "VALIDATION_COMPENSATED");
    }

    /**
     * Compensation actions for settlement request failure
     */
    private void compensateSettlementRequest(String sagaId, String packetHash) {
        log.info("Compensating settlement request: sagaId={}", sagaId);
        sagaStateManager.updateSagaState(sagaId, "SETTLEMENT_REQUEST_COMPENSATED");
    }

    /**
     * Compensation actions for settlement failure
     */
    private void compensateSettlement(String sagaId, String packetHash) {
        log.info("Compensating settlement: sagaId={}", sagaId);
        sagaStateManager.updateSagaState(sagaId, "SETTLEMENT_COMPENSATED");
    }

    /**
     * Get saga status
     */
    public SagaState getSagaStatus(String sagaId) {
        return sagaStateManager.getSagaState(sagaId);
    }

    /**
     * Find saga by packet hash (for event correlation)
     */
    public String findSagaByPacketHash(String packetHash) {
        return sagaStateManager.findSagaByPacketHash(packetHash);
    }

    /**
     * Compensate payment saga on failure
     */
    @Transactional
    public void compensatePaymentSaga(String sagaId, String packetHash, String failureReason) {
        log.info("Compensating payment saga: sagaId={}, reason={}", sagaId, failureReason);
        sagaStateManager.recordFailureReason(sagaId, failureReason);
        sagaStateManager.updateSagaState(sagaId, "SAGA_COMPENSATED");
        sagaMetrics.incrementSagaCompensated();
    }

    @Transactional
    public void markPaymentCompleted(String sagaId) {
        SagaState state = sagaStateManager.getSagaState(sagaId);
        if (state == null || "SAGA_COMPLETED".equals(state.getState())) return;
        sagaStateManager.updateSagaState(sagaId, "SAGA_COMPLETED");
        sagaMetrics.incrementSagaCompleted();
    }
}
