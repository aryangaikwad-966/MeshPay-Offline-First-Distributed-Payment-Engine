package com.demo.meshpay.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.TransactionRepository;
import org.springframework.stereotype.Component;


/**
 * Custom metrics for Payment Settlement operations
 * Tracks business metrics for monitoring and alerting
 */
@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter paymentReceivedCounter;
    private final Counter paymentValidatedCounter;
    private final Counter settlementRequestedCounter;
    private final Counter paymentSettledCounter;
    private final Counter paymentFailedCounter;
    private final Counter dlqMessageCounter;

    // Timers
    private final Timer settlementTimer;
    private final Timer validationTimer;

    public PaymentMetrics(MeterRegistry meterRegistry, TransactionRepository transactions) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.paymentReceivedCounter = Counter.builder("payment.received.total")
                .description("Total number of payment received events")
                .register(meterRegistry);

        this.paymentValidatedCounter = Counter.builder("payment.validated.total")
                .description("Total number of payment validated events")
                .register(meterRegistry);

        this.settlementRequestedCounter = Counter.builder("settlement.requested.total")
                .description("Total number of settlement requested events")
                .register(meterRegistry);

        this.paymentSettledCounter = Counter.builder("payment.settled.total")
                .description("Total number of successfully settled payments")
                .register(meterRegistry);

        this.paymentFailedCounter = Counter.builder("payment.failed.total")
                .description("Total number of failed payments")
                .register(meterRegistry);

        this.dlqMessageCounter = Counter.builder("kafka.dlq.messages.total")
                .description("Total number of messages sent to DLQ")
                .register(meterRegistry);

        // Initialize timers
        this.settlementTimer = Timer.builder("settlement.duration")
                .description("Time taken to process payment settlement")
                .register(meterRegistry);

        this.validationTimer = Timer.builder("validation.duration")
                .description("Time taken to validate payment")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("payment.pending.settlements", transactions, repository ->
                        repository.countByStatus(Transaction.Status.SYNCHRONIZED)
                                + repository.countByStatus(Transaction.Status.VALIDATED)
                                + repository.countByStatus(Transaction.Status.SETTLEMENT_REQUESTED))
                .description("Number of pending settlements")
                .register(meterRegistry);
    }

    // Counter increment methods
    public void incrementPaymentReceived() {
        paymentReceivedCounter.increment();
    }

    public void incrementPaymentValidated() {
        paymentValidatedCounter.increment();
    }

    public void incrementSettlementRequested() {
        settlementRequestedCounter.increment();
    }

    public void incrementPaymentSettled() {
        paymentSettledCounter.increment();
    }

    public void incrementPaymentFailed() {
        paymentFailedCounter.increment();
    }

    public void incrementDlqMessage() {
        dlqMessageCounter.increment();
    }

    // Timer recording methods
    public Timer.Sample startSettlementTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopSettlementTimer(Timer.Sample sample) {
        sample.stop(settlementTimer);
    }

    public Timer.Sample startValidationTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopValidationTimer(Timer.Sample sample) {
        sample.stop(validationTimer);
    }

}
