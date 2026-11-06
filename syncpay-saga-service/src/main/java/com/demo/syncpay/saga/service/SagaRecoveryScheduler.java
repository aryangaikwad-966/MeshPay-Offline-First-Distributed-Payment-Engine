package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.model.SagaCommandOutbox;
import com.demo.meshpay.saga.model.SagaState;
import com.demo.meshpay.saga.repository.SagaCommandOutboxRepository;
import com.demo.meshpay.saga.repository.SagaStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Recovery sweeper for stuck Saga workflows.
 *
 * A saga is "stuck" when it sits in a non-terminal state longer than
 * meshpay.saga.recovery.timeout without updates. Recovery is deliberately
 * conservative:
 *  - If the saga has a DEAD command-outbox row, the command is requeued once
 *    per saga (bounded by recovery_attempts). The commandId is unchanged, so
 *    consumer-side deduplication still applies.
 *  - Otherwise the saga is moved to MANUAL_REVIEW — never auto-settled and
 *    never auto-compensated, because a timeout cannot prove the payment
 *    failed or succeeded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaRecoveryScheduler {

    private static final Set<String> TERMINAL = Set.of("SAGA_COMPLETED", "SAGA_COMPENSATED", "MANUAL_REVIEW");

    private final SagaStateRepository sagaStates;
    private final SagaCommandOutboxRepository commandOutbox;
    private final SagaStateManager stateManager;
    private final MeterRegistry meterRegistry;

    @Value("${meshpay.saga.recovery.timeout:PT10M}")
    private java.time.Duration timeout;

    @Value("${meshpay.saga.recovery.max-attempts:3}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${meshpay.saga.recovery.interval-ms:60000}")
    @Transactional
    public void recoverStuckSagas() {
        Instant cutoff = Instant.now().minus(timeout);
        List<SagaState> stuck = sagaStates.findByStateNotInAndUpdatedAtBefore(TERMINAL, cutoff);
        if (stuck.isEmpty()) return;
        log.info("Saga recovery sweep found {} stuck workflow(s)", stuck.size());

        for (SagaState saga : stuck) {
            try {
                recoverOne(saga);
            } catch (Exception e) {
                log.error("Saga recovery failed for sagaId={}", saga.getSagaId(), e);
                meterRegistry.counter("meshpay.saga.recovery.failure").increment();
            }
        }
    }

    private void recoverOne(SagaState saga) {
        List<SagaCommandOutbox> dead = commandOutbox.findBySagaIdAndState(saga.getSagaId(), "DEAD");
        int attempts = saga.getRecoveryAttempts() == null ? 0 : saga.getRecoveryAttempts();

        if (!dead.isEmpty() && attempts < maxAttempts) {
            for (SagaCommandOutbox command : dead) {
                command.requeue();
                commandOutbox.save(command);
            }
            saga.setRecoveryAttempts(attempts + 1);
            saga.setLastRecoveryAt(Instant.now());
            sagaStates.save(saga);
            meterRegistry.counter("meshpay.saga.recovery.requeued").increment();
            log.warn("Requeued {} DEAD command(s) for stuck saga: sagaId={}, state={}, attempt={}",
                    dead.size(), saga.getSagaId(), saga.getState(), attempts + 1);
            return;
        }

        // No safe automatic action: park for operator review.
        stateManager.recordFailureReason(saga.getSagaId(),
                "STUCK_TIMEOUT: no update for " + timeout + " in state " + saga.getState());
        stateManager.updateSagaState(saga.getSagaId(), "MANUAL_REVIEW");
        meterRegistry.counter("meshpay.saga.recovery.manual_review").increment();
        log.warn("Moved stuck saga to MANUAL_REVIEW: sagaId={}, state={}", saga.getSagaId(), saga.getState());
    }
}
