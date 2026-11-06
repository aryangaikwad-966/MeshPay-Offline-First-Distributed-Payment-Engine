package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.api.DecryptedPaymentPacket;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.demo.meshpay.payment.events.EventEnvelope;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.demo.meshpay.payment.repository.PaymentLifecycleEventRepository;
import com.demo.meshpay.payment.model.PaymentLifecycleEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PacketPersistenceService {
    private final TransactionRepository transactions;
    private final OutboxService outboxService;
    private final PaymentLifecycleEventRepository lifecycleEvents;

    @Transactional
    public void synchronize(PaymentPacketRequest envelope, DecryptedPaymentPacket payment, String hash) {
        Instant now = Instant.now();
        Transaction transaction = new Transaction();
        transaction.setPacketHash(hash);
        transaction.setPacketId(payment.packetId());
        transaction.setSenderVpa(payment.senderVpa().toLowerCase());
        transaction.setReceiverVpa(payment.receiverVpa().toLowerCase());
        transaction.setNonce(payment.senderNonce());
        transaction.setCurrency(payment.currency());
        transaction.setAmount(payment.amount());
        transaction.setSignedAt(Instant.ofEpochMilli(payment.signedAt()));
        transaction.setSynchronizedAt(now);
        transaction.setBridgeNodeId(envelope.bridgeNodeId());
        transaction.setHopCount(envelope.hopCount());
        transaction.setStatus(Transaction.Status.SYNCHRONIZED);
        transactions.saveAndFlush(transaction);
        lifecycleEvents.save(new PaymentLifecycleEvent(hash, null, "SYNCHRONIZED", "PACKET_DURABLY_SYNCHRONIZED"));

        String eventId = UUID.randomUUID().toString();
        EventEnvelope event = EventEnvelope.builder()
                .eventId(eventId).eventType("PAYMENT_PACKET_SYNCHRONIZED")
                .aggregateId(hash).packetHash(hash).timestamp(now)
                .producer("PaymentPacketSyncService").schemaVersion("1.0")
                .payload(Map.of("packetId", payment.packetId(), "packetHash", hash,
                        "senderVpa", payment.senderVpa(), "receiverVpa", payment.receiverVpa(),
                        "amount", payment.amount(), "currency", payment.currency(),
                        "signedAt", payment.signedAt(), "senderNonce", payment.senderNonce(),
                        "status", "SYNCHRONIZED"))
                .build();
        outboxService.saveEvent(event, "payment-packet-synchronized", hash, "PaymentPacketSyncService");
    }
}
