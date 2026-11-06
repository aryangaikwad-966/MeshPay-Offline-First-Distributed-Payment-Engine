package com.demo.meshpay.saga.service;

import com.demo.meshpay.saga.events.ValidatePaymentCommand;
import com.demo.meshpay.saga.model.SagaCommandOutbox;
import com.demo.meshpay.saga.repository.SagaCommandOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCommandOutboxTest {
    @Mock SagaCommandOutboxRepository repository;

    @Test
    void commandIsPersistedWithStableIdAndCorrelationKey() {
        when(repository.existsById(anyString())).thenReturn(false, true);
        SagaCommandProducer producer = new SagaCommandProducer(repository, new ObjectMapper());
        ValidatePaymentCommand command = ValidatePaymentCommand.builder().sagaId("saga-123").packetId("packet-1")
                .packetHash("hash-1").senderVpa("a@example.test").receiverVpa("b@example.test").build();

        producer.sendValidatePaymentCommand(command);
        producer.sendValidatePaymentCommand(command);

        ArgumentCaptor<SagaCommandOutbox> saved = ArgumentCaptor.forClass(SagaCommandOutbox.class);
        org.mockito.ArgumentCaptor<String> ids = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).existsById(ids.capture());
        verify(repository, times(1)).save(saved.capture());
        assertEquals(ids.getAllValues().get(0), ids.getAllValues().get(1));
        assertEquals(ids.getValue(), saved.getValue().getCommandId());
        assertEquals("saga-123", saved.getValue().getSagaId());
        assertEquals("saga-validate-payment-command", saved.getValue().getTopic());
        assertTrue(saved.getValue().getPayload().contains("packet-1"));
    }

    @Test
    void exhaustedCommandPublicationMovesToDeadState() {
        SagaCommandOutbox command = new SagaCommandOutbox("id", "saga", "topic", "{}");
        for (int i = 0; i < 10; i++) command.failed(new IllegalStateException("broker unavailable"));
        assertEquals("DEAD", command.getState());
        assertEquals(10, command.getAttempts());
    }
}
