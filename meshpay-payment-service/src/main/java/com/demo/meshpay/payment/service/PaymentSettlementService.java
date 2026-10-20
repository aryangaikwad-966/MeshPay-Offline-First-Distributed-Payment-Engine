package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.events.EventEnvelope;
import com.demo.meshpay.payment.events.PaymentFailedEvent;
import com.demo.meshpay.payment.events.PaymentSettledEvent;
import com.demo.meshpay.payment.events.PaymentValidatedEvent;
import com.demo.meshpay.payment.metrics.PaymentMetrics;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.demo.meshpay.payment.model.PaymentLifecycleEvent;
import com.demo.meshpay.payment.repository.PaymentLifecycleEventRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for orchestrating event-driven payment settlement flow
 * Uses the transactional outbox pattern to ensure reliable event publishing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSettlementService {

    private final OutboxService outboxService;
    private final PaymentMetrics paymentMetrics;
    private final TransactionRepository transactionRepository;
    private final PaymentLifecycleEventRepository lifecycleEvents;

    /**
     * Validate a payment
     * Called when Saga Service sends validate command via Kafka
     */
    @Transactional
    public void validatePayment(String packetId, String packetHash, String senderVpa, String receiverVpa, 
                                BigDecimal amount, String nonce, Long signedAt, String parentEventId) {
        Timer.Sample timer = paymentMetrics.startValidationTimer();
        try {
            String eventId = UUID.randomUUID().toString();
            String aggregateId = packetHash;
            
            log.info("Validating payment: packetHash={}", packetHash);

            Transaction transaction = requirePayment(packetId, packetHash, senderVpa, receiverVpa, amount, nonce, signedAt);
            if (transaction.getStatus() == Transaction.Status.VALIDATED ||
                    transaction.getStatus() == Transaction.Status.SETTLEMENT_REQUESTED ||
                    transaction.getStatus() == Transaction.Status.SETTLED) {
                paymentMetrics.stopValidationTimer(timer);
                return;
            }
            if (transaction.getStatus() != Transaction.Status.SYNCHRONIZED)
                throw new IllegalStateException("Payment is not in a synchronizable state");

            boolean isValid = validatePaymentLogic(senderVpa, receiverVpa, amount);
            
            if (isValid) {
                recordTransition(transaction, Transaction.Status.VALIDATED, "PAYMENT_VALIDATED");
                transaction.setStatus(Transaction.Status.VALIDATED);
                transactionRepository.save(transaction);
                // Create payment validated event
                PaymentValidatedEvent paymentValidatedEvent = PaymentValidatedEvent.builder()
                        .packetId(packetId)
                        .packetHash(packetHash)
                        .senderVpa(senderVpa)
                        .receiverVpa(receiverVpa)
                        .amount(amount)
                        .nonce(nonce)
                        .signedAt(signedAt)
                        .validatedAt(Instant.now())
                        .build();
                
                // Create event envelope
                EventEnvelope eventEnvelope = EventEnvelope.builder()
                        .eventId(eventId)
                        .eventType("PAYMENT_VALIDATED")
                        .aggregateId(aggregateId)
                        .packetHash(packetHash)
                        .correlationId(parentEventId)
                        .timestamp(Instant.now())
                        .producer("PaymentSettlementService")
                        .schemaVersion("1.0")
                        .payload(paymentValidatedEvent)
                        .build();
                
                // Save to outbox
                outboxService.saveEvent(
                        eventEnvelope, 
                        "payment-validated", 
                        packetHash, 
                        "PaymentSettlementService"
                );
                
                log.info("Payment validated event saved to outbox: eventId={}", eventId);
                paymentMetrics.incrementPaymentValidated();
                paymentMetrics.stopValidationTimer(timer);
                
            } else {
                handlePaymentFailure(packetId, packetHash, "VALIDATION_FAILED", "Validation failed", parentEventId);
            }
            
        } catch (Exception e) {
            log.error("Error validating payment: packetHash={}", packetHash, e);
            paymentMetrics.incrementPaymentFailed();
            paymentMetrics.stopValidationTimer(timer);
            throw new RuntimeException("Failed to validate payment", e);
        }
    }

    /**
     * Request settlement for a validated payment
     * Called when Saga Service sends request settlement command via Kafka
     */
    @Transactional
    public void requestSettlement(String packetId, String packetHash, String senderVpa, String receiverVpa, 
                                  BigDecimal amount, String nonce, Long signedAt, String parentEventId) {
        Timer.Sample timer = paymentMetrics.startSettlementTimer();
        try {
            String eventId = UUID.randomUUID().toString();
            String aggregateId = packetHash;
            
            log.info("Requesting settlement: packetHash={}", packetHash);
            Transaction transaction = requirePayment(packetId, packetHash, senderVpa, receiverVpa, amount, nonce, signedAt);
            if (transaction.getStatus() == Transaction.Status.SETTLEMENT_REQUESTED ||
                    transaction.getStatus() == Transaction.Status.SETTLED) {
                paymentMetrics.stopSettlementTimer(timer);
                return;
            }
            if (transaction.getStatus() != Transaction.Status.VALIDATED)
                throw new IllegalStateException("Payment must be validated before settlement is requested");
            recordTransition(transaction, Transaction.Status.SETTLEMENT_REQUESTED, "SETTLEMENT_REQUESTED");
            transaction.setStatus(Transaction.Status.SETTLEMENT_REQUESTED);
            transactionRepository.save(transaction);
            paymentMetrics.incrementSettlementRequested();
            
            // Create settlement request event
            com.demo.meshpay.payment.events.PaymentSettlementRequestedEvent settlementRequestedEvent = 
                com.demo.meshpay.payment.events.PaymentSettlementRequestedEvent.builder()
                        .packetId(packetId)
                        .packetHash(packetHash)
                        .senderVpa(senderVpa)
                        .receiverVpa(receiverVpa)
                        .amount(amount)
                        .nonce(nonce)
                        .signedAt(signedAt)
                        .requestedAt(Instant.now())
                        .build();
            
            // Create event envelope
            EventEnvelope eventEnvelope = EventEnvelope.builder()
                    .eventId(eventId)
                    .eventType("PAYMENT_SETTLEMENT_REQUESTED")
                    .aggregateId(aggregateId)
                    .packetHash(packetHash)
                    .correlationId(parentEventId)
                    .timestamp(Instant.now())
                    .producer("PaymentSettlementService")
                    .schemaVersion("1.0")
                    .payload(settlementRequestedEvent)
                    .build();
            
            // Save to outbox
            outboxService.saveEvent(
                    eventEnvelope, 
                    "payment-settlement-requested", 
                    packetHash, 
                    "PaymentSettlementService"
            );
            
            log.info("Settlement requested event saved to outbox: eventId={}", eventId);
            paymentMetrics.stopSettlementTimer(timer);
            
        } catch (Exception e) {
            log.error("Error requesting settlement: packetHash={}", packetHash, e);
            paymentMetrics.incrementPaymentFailed();
            paymentMetrics.stopSettlementTimer(timer);
            throw new RuntimeException("Failed to request settlement", e);
        }
    }

    /**
     * Mark a payment as settled
     * Called when Saga Service sends complete settlement command via Kafka
     */
    @Transactional
    public void markPaymentSettled(String packetId, String packetHash, String senderVpa, String receiverVpa, 
                                   BigDecimal amount, String parentEventId) {
        try {
            String eventId = UUID.randomUUID().toString();
            String aggregateId = packetHash;
            
            log.info("Marking payment as settled: packetHash={}", packetHash);
            Transaction transaction = transactionRepository.findByPacketHash(packetHash)
                    .orElseThrow(() -> new IllegalArgumentException("Synchronized payment was not found"));
            if (!transaction.getPacketId().equals(packetId) || transaction.getAmount().compareTo(amount) != 0 ||
                    !transaction.getSenderVpa().equalsIgnoreCase(senderVpa) ||
                    !transaction.getReceiverVpa().equalsIgnoreCase(receiverVpa))
                throw new IllegalArgumentException("Settlement command does not match the synchronized payment");
            if (transaction.getStatus() == Transaction.Status.SETTLED) return;
            if (transaction.getStatus() != Transaction.Status.SETTLEMENT_REQUESTED)
                throw new IllegalStateException("Settlement was not requested for this payment");
            recordTransition(transaction, Transaction.Status.SETTLED, "SETTLEMENT_CONFIRMED");
            transaction.setStatus(Transaction.Status.SETTLED);
            transaction.setSettledAt(Instant.now());
            transactionRepository.save(transaction);
            paymentMetrics.incrementPaymentSettled();
            
            // Create payment settled event
            PaymentSettledEvent paymentSettledEvent = PaymentSettledEvent.builder()
                    .packetId(packetId)
                    .packetHash(packetHash)
                    .senderVpa(senderVpa)
                    .receiverVpa(receiverVpa)
                    .amount(amount)
                    .settledAt(Instant.now())
                    .build();
            
            // Create event envelope
            EventEnvelope eventEnvelope = EventEnvelope.builder()
                    .eventId(eventId)
                    .eventType("PAYMENT_SETTLED")
                    .aggregateId(aggregateId)
                    .packetHash(packetHash)
                    .correlationId(parentEventId)
                    .timestamp(Instant.now())
                    .producer("PaymentSettlementService")
                    .schemaVersion("1.0")
                    .payload(paymentSettledEvent)
                    .build();
            
            // Save to outbox
            outboxService.saveEvent(
                    eventEnvelope, 
                    "payment-settled", 
                    packetHash, 
                    "PaymentSettlementService"
            );
            
            log.info("Payment settled event saved to outbox: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error marking payment as settled: packetHash={}", packetHash, e);
            paymentMetrics.incrementPaymentFailed();
            throw new RuntimeException("Failed to mark payment as settled", e);
        }
    }

    /**
     * Handle payment failure
     */
    @Transactional
    public void handlePaymentFailure(String packetId, String packetHash, String failureType, String failureReason, String parentEventId) {
        try {
            Transaction transaction = transactionRepository.findByPacketHash(packetHash).orElse(null);
            if (transaction != null) {
                if (transaction.getStatus() == Transaction.Status.SETTLED)
                    throw new IllegalStateException("A completed payment cannot be marked rejected by compensation");
                if (transaction.getStatus() == Transaction.Status.REJECTED) return;
                recordTransition(transaction, Transaction.Status.REJECTED, failureType);
                transaction.setStatus(Transaction.Status.REJECTED);
                transactionRepository.save(transaction);
            }
            String eventId = UUID.randomUUID().toString();
            String aggregateId = packetHash;
            
            log.error("Handling payment failure: packetHash={}, failureType={}", packetHash, failureType);
            paymentMetrics.incrementPaymentFailed();
            
            // Create payment failed event
            PaymentFailedEvent paymentFailedEvent = PaymentFailedEvent.builder()
                    .packetId(packetId)
                    .packetHash(packetHash)
                    .failureReason(failureReason)
                    .failureType(failureType)
                    .failedAt(Instant.now())
                    .build();
            
            // Create event envelope
            EventEnvelope eventEnvelope = EventEnvelope.builder()
                    .eventId(eventId)
                    .eventType("PAYMENT_FAILED")
                    .aggregateId(aggregateId)
                    .packetHash(packetHash)
                    .correlationId(parentEventId)
                    .timestamp(Instant.now())
                    .producer("PaymentSettlementService")
                    .schemaVersion("1.0")
                    .payload(paymentFailedEvent)
                    .build();
            
            // Save to outbox
            outboxService.saveEvent(
                    eventEnvelope, 
                    "payment-failed", 
                    packetHash, 
                    "PaymentSettlementService"
            );
            
            log.info("Payment failed event saved to outbox: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error handling payment failure: packetHash={}", packetHash, e);
            throw new RuntimeException("Failed to handle payment failure", e);
        }
    }

    /**
     * Simplified validation logic
     * In a real system, this would check balances, limits, KYC, etc.
     */
    private boolean validatePaymentLogic(String senderVpa, String receiverVpa, BigDecimal amount) {
        log.debug("Validating payment: sender={}, receiver={}, amount={}", senderVpa, receiverVpa, amount);
        return senderVpa != null && receiverVpa != null && !senderVpa.equalsIgnoreCase(receiverVpa) &&
                amount != null && amount.signum() > 0 && amount.scale() <= 2;
    }

    private Transaction requirePayment(String packetId, String packetHash, String senderVpa, String receiverVpa,
                                       BigDecimal amount, String nonce, Long signedAt) {
        Transaction payment = transactionRepository.findByPacketHash(packetHash)
                .orElseThrow(() -> new IllegalArgumentException("Synchronized payment was not found"));
        if (!payment.getPacketId().equals(packetId) || !payment.getSenderVpa().equalsIgnoreCase(senderVpa) ||
                !payment.getReceiverVpa().equalsIgnoreCase(receiverVpa) || payment.getAmount().compareTo(amount) != 0 ||
                !payment.getNonce().equals(nonce) || !payment.getSignedAt().equals(Instant.ofEpochMilli(signedAt)))
            throw new IllegalArgumentException("Command does not match the synchronized payment");
        return payment;
    }

    private void recordTransition(Transaction transaction, Transaction.Status to, String reason) {
        lifecycleEvents.save(new PaymentLifecycleEvent(transaction.getPacketHash(),
                transaction.getStatus().name(), to.name(), reason));
    }
}
