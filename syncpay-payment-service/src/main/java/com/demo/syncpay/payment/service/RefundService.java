package com.demo.syncpay.payment.service;

import com.demo.syncpay.payment.events.EventEnvelope;
import com.demo.syncpay.payment.model.Refund;
import com.demo.syncpay.payment.model.Transaction;
import com.demo.syncpay.payment.repository.RefundRepository;
import com.demo.syncpay.payment.repository.TransactionRepository;
import com.demo.syncpay.payment.model.PaymentLifecycleEvent;
import com.demo.syncpay.payment.repository.PaymentLifecycleEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full refunds for settled payments. Creates compensating ledger entries
 * (never mutates originals), restores simulated balances, and is idempotent
 * via the unique refunds.packet_hash constraint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final TransactionRepository transactions;
    private final RefundRepository refunds;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final PaymentLifecycleEventRepository lifecycleEvents;

    public static class RefundException extends RuntimeException {
        private final int status;
        public RefundException(String message, int status) { super(message); this.status = status; }
        public int getStatus() { return status; }
    }

    @Transactional
    public Refund refund(String identifier, String reason, String requestedBy) {
        Transaction transaction = transactions.findByPacketId(identifier)
                .or(() -> transactions.findByPacketHash(identifier))
                .orElseThrow(() -> new RefundException("Payment not found: " + identifier, 404));

        // Idempotent: an existing refund for this payment is returned as-is.
        var existing = refunds.findByPacketHash(transaction.getPacketHash());
        if (existing.isPresent()) return existing.get();

        if (transaction.getStatus() != Transaction.Status.SETTLED)
            throw new RefundException("Only SETTLED payments can be refunded (current: "
                    + transaction.getStatus() + ")", 409);

        Refund refund = new Refund(transaction.getPacketHash(), transaction.getId(),
                transaction.getAmount(), transaction.getCurrency(), reason, requestedBy);
        try {
            refunds.saveAndFlush(refund); // surface unique violation before financial mutation
        } catch (DataIntegrityViolationException e) {
            return refunds.findByPacketHash(transaction.getPacketHash())
                    .orElseThrow(() -> e);
        }

        try {
            ledgerService.postRefund(transaction);
        } catch (LedgerService.InsufficientFundsException | LedgerService.AccountNotFoundException e) {
            throw new RefundException("Refund cannot be completed: " + e.getMessage(), 409);
        }

        transaction.setStatus(Transaction.Status.REFUNDED);
        transaction.setRefundedAt(Instant.now());
        transaction.setRefundReason(reason);
        transactions.save(transaction);
        lifecycleEvents.save(new PaymentLifecycleEvent(transaction.getPacketHash(),
                Transaction.Status.SETTLED.name(), Transaction.Status.REFUNDED.name(), "FULL_REFUND"));

        publishRefunded(transaction, refund);
        log.info("Payment refunded: packetHash={}, refundId={}, amount={}",
                transaction.getPacketHash(), refund.getId(), refund.getAmount());
        return refund;
    }

    private void publishRefunded(Transaction transaction, Refund refund) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope envelope = EventEnvelope.builder()
                .eventId(eventId)
                .eventType("PAYMENT_REFUNDED")
                .aggregateId(transaction.getPacketHash())
                .packetHash(transaction.getPacketHash())
                .timestamp(Instant.now())
                .producer("RefundService")
                .schemaVersion("1.0")
                .payload(Map.of(
                        "packetId", transaction.getPacketId(),
                        "packetHash", transaction.getPacketHash(),
                        "refundId", refund.getId(),
                        "amount", refund.getAmount(),
                        "currency", refund.getCurrency(),
                        "reason", refund.getReason() == null ? "" : refund.getReason(),
                        "refundedAt", Instant.now().toString()))
                .build();
        outboxService.saveEvent(envelope, "payment-refunded", transaction.getPacketHash(), "RefundService");
    }
}
