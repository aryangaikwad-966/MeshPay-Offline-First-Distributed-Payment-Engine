package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.model.SagaState;
import com.demo.meshpay.saga.repository.SagaStateRepository;
import com.demo.meshpay.saga.model.SagaTransition;
import com.demo.meshpay.saga.repository.SagaTransitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Manages Saga state for distributed transactions
 * Uses persistent database storage for microservices architecture
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaStateManager {

    private final SagaStateRepository sagaStateRepository;
    private final SagaTransitionRepository transitionRepository;

    /**
     * Create a new saga state
     */
    @Transactional
    public void createSaga(String sagaId, String aggregateId, String initialState) {
        SagaState state = SagaState.builder()
                .sagaId(sagaId)
                .packetHash(aggregateId)
                .aggregateId(aggregateId)
                .state(initialState)
                .currentStep(initialState)
                .build();
        
        sagaStateRepository.save(state);
        transitionRepository.save(new SagaTransition(sagaId, null, initialState, "SAGA_CREATED"));
        log.info("Created saga state: sagaId={}, state={}", sagaId, initialState);
    }

    @Transactional
    public void createSynchronizedPaymentSaga(String sagaId, String packetHash, String packetId,
            String senderVpa, String receiverVpa, java.math.BigDecimal amount, String currency,
            String nonce, Long signedAt) {
        SagaState state = SagaState.builder().sagaId(sagaId).packetHash(packetHash).aggregateId(packetHash)
                .packetId(packetId).senderVpa(senderVpa).receiverVpa(receiverVpa).amount(amount)
                .currency(currency).nonce(nonce).signedAt(signedAt).state("PAYMENT_RECEIVED")
                .currentStep("PAYMENT_RECEIVED").build();
        sagaStateRepository.saveAndFlush(state);
        transitionRepository.save(new SagaTransition(sagaId, null, "PAYMENT_RECEIVED", "PACKET_SYNCHRONIZED"));
    }

    /**
     * Update saga state
     */
    @Transactional
    public void updateSagaState(String sagaId, String newState) {
        Optional<SagaState> stateOpt = sagaStateRepository.findBySagaId(sagaId);
        if (stateOpt.isPresent()) {
            SagaState state = stateOpt.get();
            String current = state.getState();
            if (current.equals(newState)) return;
            if (!isAllowedTransition(current, newState))
                throw new IllegalStateException("Invalid Saga state transition: " + current + " -> " + newState);
            transitionRepository.save(new SagaTransition(sagaId, current, newState, null));
            state.setState(newState);
            state.setCurrentStep(newState);
            state.setUpdatedAt(Instant.now());
            sagaStateRepository.save(state);
            log.info("Updated saga state: sagaId={}, newState={}", sagaId, newState);
        } else {
            throw new IllegalArgumentException("Saga state not found: " + sagaId);
        }
    }

    @Transactional
    public void recordFailureReason(String sagaId, String reason) {
        SagaState state = sagaStateRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga state not found: " + sagaId));
        state.setFailureReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)));
        sagaStateRepository.save(state);
    }

    /**
     * Get saga state
     */
    public SagaState getSagaState(String sagaId) {
        return sagaStateRepository.findBySagaId(sagaId).orElse(null);
    }

    /**
     * Delete saga state
     */
    @Transactional
    public void deleteSaga(String sagaId) {
        sagaStateRepository.deleteBySagaId(sagaId);
        log.info("Deleted saga state: sagaId={}", sagaId);
    }

    /**
     * Get saga state by packet hash
     */
    public SagaState getSagaStateByPacketHash(String packetHash) {
        return sagaStateRepository.findByPacketHash(packetHash).orElse(null);
    }

    /**
     * Find saga ID by packet hash (for event correlation)
     */
    public String findSagaByPacketHash(String packetHash) {
        Optional<SagaState> state = sagaStateRepository.findByPacketHash(packetHash);
        return state.map(SagaState::getSagaId).orElse(null);
    }

    private boolean isAllowedTransition(String from, String to) {
        if (to.equals("SAGA_COMPENSATED") || to.endsWith("_FAILED"))
            return !from.equals("SAGA_COMPLETED") && !from.equals("SAGA_COMPENSATED");
        return switch (from) {
            case "PAYMENT_RECEIVED" -> to.equals("VALIDATING_PAYMENT") || to.equals("SAGA_START_FAILED");
            case "VALIDATING_PAYMENT" -> to.equals("PAYMENT_VALIDATION_COMMAND_SENT");
            case "PAYMENT_VALIDATION_COMMAND_SENT" -> to.equals("REQUESTING_SETTLEMENT");
            case "REQUESTING_SETTLEMENT" -> to.equals("SETTLEMENT_REQUEST_COMMAND_SENT");
            case "SETTLEMENT_REQUEST_COMMAND_SENT" -> to.equals("COMPLETING_SETTLEMENT");
            case "COMPLETING_SETTLEMENT" -> to.equals("SETTLEMENT_COMPLETION_COMMAND_SENT");
            case "SETTLEMENT_COMPLETION_COMMAND_SENT" -> to.equals("SAGA_COMPLETED") || to.equals("SETTLEMENT_COMPENSATED");
            case "PAYMENT_VALIDATION_FAILED" -> to.equals("VALIDATION_COMPENSATED");
            case "SETTLEMENT_REQUEST_FAILED" -> to.equals("SETTLEMENT_REQUEST_COMPENSATED");
            case "SETTLEMENT_COMPLETION_FAILED" -> to.equals("SETTLEMENT_COMPENSATED");
            default -> false;
        };
    }
}
