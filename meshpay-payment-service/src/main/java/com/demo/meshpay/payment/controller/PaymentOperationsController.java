package com.demo.meshpay.payment.controller;

import com.demo.meshpay.payment.model.Outbox;
import com.demo.meshpay.payment.model.PacketAttempt;
import com.demo.meshpay.payment.model.PaymentLifecycleEvent;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.OutboxRepository;
import com.demo.meshpay.payment.repository.PacketAttemptRepository;
import com.demo.meshpay.payment.repository.PaymentLifecycleEventRepository;
import com.demo.meshpay.payment.repository.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/payment/ops")
@RequiredArgsConstructor
public class PaymentOperationsController {
    private final TransactionRepository transactions;
    private final PacketAttemptRepository attempts;
    private final OutboxRepository outbox;
    private final PaymentLifecycleEventRepository lifecycleEvents;

    @GetMapping("/overview")
    @Operation(summary = "Current payment and synchronization counts from PostgreSQL")
    public Overview overview() {
        long pending = transactions.countByStatus(Transaction.Status.SYNCHRONIZED)
                + transactions.countByStatus(Transaction.Status.VALIDATED)
                + transactions.countByStatus(Transaction.Status.SETTLEMENT_REQUESTED);
        return new Overview(transactions.count(), transactions.countByStatus(Transaction.Status.SETTLED),
                attempts.countByOutcome(PacketAttempt.Outcome.REJECTED),
                attempts.countByOutcome(PacketAttempt.Outcome.DUPLICATE), pending,
                transactions.countByStatus(Transaction.Status.REJECTED), outbox.countByProcessedFalse(),
                outbox.countByDeadLetterTrue());
    }

    @GetMapping("/transactions")
    @Operation(summary = "Search recent synchronized payments")
    public List<TransactionView> transactions(@RequestParam(required = false) String search,
            @RequestParam(required = false) Transaction.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return transactions.findTop100ByOrderByIdDesc().stream()
                .filter(t -> search == null || search.isBlank() || contains(t.getPacketId(), search) || contains(t.getPacketHash(), search))
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> from == null || !t.getSynchronizedAt().isBefore(from))
                .filter(t -> to == null || !t.getSynchronizedAt().isAfter(to))
                .map(PaymentOperationsController::view).toList();
    }

    @GetMapping("/transactions/{identifier}")
    @Operation(summary = "Payment details and persisted lifecycle history")
    public ResponseEntity<TransactionDetail> transaction(@PathVariable String identifier) {
        Transaction transaction = transactions.findByPacketId(identifier)
                .or(() -> transactions.findByPacketHash(identifier)).orElse(null);
        if (transaction == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new TransactionDetail(view(transaction),
                lifecycleEvents.findByPacketHashOrderByOccurredAtAsc(transaction.getPacketHash()).stream()
                        .map(PaymentOperationsController::view).toList()));
    }

    @GetMapping("/attempts")
    public List<AttemptView> attempts() {
        return attempts.findTop100ByOrderByAttemptedAtDesc().stream()
                .map(a -> new AttemptView(a.getPacketHash(), a.getSenderVpa(), a.getOutcome().name(), a.getReason(), a.getAttemptedAt())).toList();
    }

    @GetMapping("/outbox")
    public List<OutboxView> outbox() {
        return outbox.findTop100ByOrderByCreatedAtDesc().stream()
                .map(o -> new OutboxView(o.getEventId(), o.getEventType(), o.getTopic(), o.getRetryCount(),
                        o.getProcessed(), o.getDeadLetter(), o.getNextAttemptAt(), safeError(o.getErrorMessage()), o.getCreatedAt())).toList();
    }

    private static boolean contains(String source, String search) { return source != null && source.toLowerCase().contains(search.toLowerCase()); }
    private static TransactionView view(Transaction t) {
        return new TransactionView(t.getPacketId(), t.getPacketHash(), t.getSenderVpa(), t.getReceiverVpa(),
                t.getAmount(), t.getCurrency(), t.getStatus().name(), t.getSignedAt(), t.getSynchronizedAt(),
                t.getSettledAt(), t.getBridgeNodeId(), t.getHopCount());
    }
    private static LifecycleView view(PaymentLifecycleEvent e) {
        return new LifecycleView(e.getFromState(), e.getToState(), e.getReason(), e.getOccurredAt());
    }
    private static String safeError(String error) {
        if (error == null) return null;
        int delimiter = error.indexOf(':');
        return delimiter < 0 ? "PUBLICATION_FAILED" : error.substring(0, delimiter);
    }

    public record Overview(long totalPayments, long settledPayments, long rejectedAttempts, long duplicatesDetected,
                           long pendingProcessing, long rejectedPayments, long outboxPending, long outboxDeadLetter) {}
    public record TransactionView(String packetId, String packetHash, String senderVpa, String receiverVpa,
                                 java.math.BigDecimal amount, String currency, String status, Instant signedAt,
                                 Instant synchronizedAt, Instant settledAt, String bridgeNodeId, int hopCount) {}
    public record LifecycleView(String fromState, String toState, String reason, Instant occurredAt) {}
    public record TransactionDetail(TransactionView transaction, List<LifecycleView> history) {}
    public record AttemptView(String packetHash, String senderVpa, String outcome, String reason, Instant attemptedAt) {}
    public record OutboxView(String eventId, String eventType, String topic, int retryCount, boolean processed,
                             boolean deadLetter, Instant nextAttemptAt, String errorCode, Instant createdAt) {}
}
