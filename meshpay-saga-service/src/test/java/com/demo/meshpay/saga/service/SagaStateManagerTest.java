package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.model.SagaState;
import com.demo.meshpay.saga.repository.SagaStateRepository;
import com.demo.meshpay.saga.repository.SagaTransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SagaStateManagerTest {
    private final SagaStateRepository states = mock(SagaStateRepository.class);
    private final SagaTransitionRepository transitions = mock(SagaTransitionRepository.class);
    private SagaStateManager manager;
    private SagaState state;

    @BeforeEach
    void setUp() {
        manager = new SagaStateManager(states, transitions);
        state = SagaState.builder().sagaId("saga-1").packetHash("hash-1").state("PAYMENT_RECEIVED").build();
        when(states.findBySagaId("saga-1")).thenReturn(Optional.of(state));
    }

    @Test
    void validTransitionUpdatesStateAndPersistsHistory() {
        manager.updateSagaState("saga-1", "VALIDATING_PAYMENT");

        assertEquals("VALIDATING_PAYMENT", state.getState());
        verify(transitions).save(any());
        verify(states).save(state);
    }

    @Test
    void invalidTransitionIsRejectedWithoutChangingState() {
        assertThrows(IllegalStateException.class,
                () -> manager.updateSagaState("saga-1", "SAGA_COMPLETED"));

        assertEquals("PAYMENT_RECEIVED", state.getState());
        verify(transitions, never()).save(any());
        verify(states, never()).save(any());
    }

    @Test
    void repeatingCurrentStateDoesNotCreateDuplicateHistory() {
        manager.updateSagaState("saga-1", "PAYMENT_RECEIVED");

        verifyNoInteractions(transitions);
        verify(states, never()).save(any());
    }
}
