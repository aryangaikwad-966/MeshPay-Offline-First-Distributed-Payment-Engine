package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.api.DecryptedPaymentPacket;
import com.demo.meshpay.payment.api.PacketSyncResponse;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.demo.meshpay.payment.metrics.PacketMetrics;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.demo.meshpay.payment.security.PacketCryptography;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentPacketSyncServiceTest {
    private final PacketCryptography cryptography = mock(PacketCryptography.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final PacketPersistenceService persistence = mock(PacketPersistenceService.class);
    private final PacketAttemptRecorder attempts = mock(PacketAttemptRecorder.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private PaymentPacketSyncService service;
    private final PaymentPacketRequest envelope = new PaymentPacketRequest(
            "packet-1", "alice@demo", "nonce-1", "aXY=", "a2V5", "Y2lwaGVydGV4dA==", "c2ln", "bridge-1", 0);
    private final DecryptedPaymentPacket payment = new DecryptedPaymentPacket(
            "packet-1", "alice@demo", "bob@demo", new BigDecimal("5.25"), "USD", 1_800_000_000_000L, "nonce-1");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(cryptography.verifyAndDecrypt(envelope)).thenReturn(new PacketCryptography.VerifiedPacket(payment, "a".repeat(64)));
        when(transactions.findByPacketHash(anyString())).thenReturn(Optional.empty());
        when(transactions.findBySenderVpaAndNonce(anyString(), anyString())).thenReturn(Optional.empty());
        service = new PaymentPacketSyncService(cryptography, redis, transactions, persistence,
                attempts, new PacketMetrics(meterRegistry));
    }

    @Test
    void redisUnavailableFallsBackToPostgresBackedPersistence() {
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        PacketSyncResponse response = service.synchronize(envelope);

        assertEquals("SYNCHRONIZED", response.status());
        verify(persistence).synchronize(envelope, payment, "a".repeat(64));
        assertEquals(1.0, meterRegistry.get("meshpay.idempotency.redis.fallback").counter().count());
    }

    @Test
    void activeClaimReturnsInProgressWithoutCreatingPayment() {
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(false);

        PacketSyncResponse response = service.synchronize(envelope);

        assertEquals("IN_PROGRESS", response.status());
        verifyNoInteractions(persistence);
    }

    @Test
    void databaseFailureIsNeverReportedAsSynchronizationSuccess() {
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        doThrow(new IllegalStateException("database write failed"))
                .when(persistence).synchronize(envelope, payment, "a".repeat(64));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.synchronize(envelope));

        assertEquals("database write failed", failure.getMessage());
        verify(persistence).synchronize(envelope, payment, "a".repeat(64));
        verify(attempts, never()).record(any(), any(), eq(com.demo.meshpay.payment.model.PacketAttempt.Outcome.ACCEPTED), any());
    }
}
