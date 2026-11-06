package com.demo.syncpay.saga.service;

import com.demo.syncpay.saga.model.SagaCommandOutbox;
import com.demo.syncpay.saga.model.SagaState;
import com.demo.syncpay.saga.repository.SagaCommandOutboxRepository;
import com.demo.syncpay.saga.repository.SagaStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaRecoverySchedulerTest {

    @Mock SagaStateRepository sagaStates;
    @Mock SagaCommandOutboxRepository commandOutbox;
    @Mock SagaStateManager stateManager;

    private SagaRecoveryScheduler scheduler;
    private MeterRegistry meters = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        scheduler = new SagaRecoveryScheduler(sagaStates, commandOutbox, stateManager, meters);
        ReflectionTestUtils.setField(scheduler, "timeout", Duration.ofMinutes(10));
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 3);
    }

    private SagaCommandOutbox deadCommand(String commandId, String sagaId) {
        SagaCommandOutbox command = new SagaCommandOutbox(commandId, sagaId, "saga-request-settlement-command", "{}");
        for (int i = 0; i < 10; i++) command.failed(new RuntimeException("kafka down"));
        assertEquals("DEAD", command.getState());
        return command;
    }

    private SagaState stuck(String sagaId, String state) {
        return SagaState.builder()
                .sagaId(sagaId).packetHash("hash-" + sagaId)
                .state(state).updatedAt(Instant.now().minus(Duration.ofHours(1)))
                .recoveryAttempts(0).build();
    }

    @Test
    void deadCommandIsRequeuedWithBoundedAttempts() {
        SagaState saga = stuck("saga-1", "REQUESTING_SETTLEMENT");
        SagaCommandOutbox dead = deadCommand("saga-1:SETTLEMENT", "saga-1");
        when(sagaStates.findByStateNotInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(saga));
        when(commandOutbox.findBySagaIdAndState("saga-1", "DEAD")).thenReturn(List.of(dead));

        scheduler.recoverStuckSagas();

        assertEquals("PENDING", dead.getState());
        verify(commandOutbox).save(dead);
        verify(sagaStates).save(argThat(s -> s.getRecoveryAttempts() == 1));
        verify(stateManager, never()).updateSagaState(anyString(), eq("MANUAL_REVIEW"));
    }

    @Test
    void sagaWithoutDeadCommandGoesToManualReview() {
        SagaState saga = stuck("saga-2", "COMPLETING_SETTLEMENT");
        when(sagaStates.findByStateNotInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(saga));
        when(commandOutbox.findBySagaIdAndState("saga-2", "DEAD")).thenReturn(List.of());

        scheduler.recoverStuckSagas();

        verify(stateManager).recordFailureReason(eq("saga-2"), contains("STUCK_TIMEOUT"));
        verify(stateManager).updateSagaState("saga-2", "MANUAL_REVIEW");
        verify(sagaStates, never()).save(any());
    }

    @Test
    void maxAttemptsExceededGoesToManualReview() {
        SagaState saga = stuck("saga-3", "REQUESTING_SETTLEMENT");
        saga.setRecoveryAttempts(3);
        SagaCommandOutbox dead = deadCommand("saga-3:X", "saga-3");
        when(sagaStates.findByStateNotInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(saga));
        when(commandOutbox.findBySagaIdAndState("saga-3", "DEAD")).thenReturn(List.of(dead));

        scheduler.recoverStuckSagas();

        assertEquals("DEAD", dead.getState());
        verify(stateManager).updateSagaState("saga-3", "MANUAL_REVIEW");
    }

    @Test
    void emptySweepDoesNothing() {
        when(sagaStates.findByStateNotInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());
        scheduler.recoverStuckSagas();
        verifyNoInteractions(commandOutbox, stateManager);
    }

    @Test
    void oneFailureDoesNotBlockOtherSagas() {
        SagaState bad = stuck("saga-bad", "X");
        SagaState good = stuck("saga-good", "Y");
        when(sagaStates.findByStateNotInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(bad, good));
        when(commandOutbox.findBySagaIdAndState(eq("saga-bad"), eq("DEAD"))).thenThrow(new RuntimeException("db"));
        when(commandOutbox.findBySagaIdAndState(eq("saga-good"), eq("DEAD"))).thenReturn(List.of());

        scheduler.recoverStuckSagas();

        verify(stateManager).updateSagaState("saga-good", "MANUAL_REVIEW");
    }
}
