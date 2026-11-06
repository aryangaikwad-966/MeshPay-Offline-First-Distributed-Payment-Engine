package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.model.Account;
import com.demo.meshpay.payment.model.LedgerEntry;
import com.demo.meshpay.payment.model.Outbox;
import com.demo.meshpay.payment.model.Refund;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.AccountRepository;
import com.demo.meshpay.payment.repository.LedgerEntryRepository;
import com.demo.meshpay.payment.repository.OutboxRepository;
import com.demo.meshpay.payment.repository.RefundRepository;
import com.demo.meshpay.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Financial correctness tests: settlement moves simulated value atomically,
 * the ledger is immutable and exactly-once, refunds compensate, and DLT
 * replay resets outbox state safely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("dev")
class FinancialCorrectnessIntegrationTest {

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final String SENDER = "alice@meshpay.local";
    private static final String RECEIVER = "recipient@meshpay.local";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.data.redis.password", () -> "");
        properties.add("spring.kafka.listener.auto-startup", () -> "false");
        properties.add("eureka.client.enabled", () -> "false");
        properties.add("meshpay.outbox.enabled", () -> "false");
    }

    @Autowired PaymentSettlementService settlementService;
    @Autowired RefundService refundService;
    @Autowired OutboxRecoveryService recoveryService;
    @Autowired TransactionRepository transactions;
    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledger;
    @Autowired RefundRepository refunds;
    @Autowired OutboxRepository outbox;

    @BeforeEach
    void resetBalances() {
        Account sender = accounts.findByVpa(SENDER).orElseThrow();
        sender.setBalance(new BigDecimal("50000.00"));
        accounts.save(sender);
        Account receiver = accounts.findByVpa(RECEIVER).orElseThrow();
        receiver.setBalance(BigDecimal.ZERO);
        accounts.save(receiver);
    }

    private Transaction paymentAwaitingSettlement(String packetId, String amount) {
        Transaction t = new Transaction();
        t.setPacketHash("hash-" + packetId);
        t.setPacketId(packetId);
        t.setSenderVpa(SENDER);
        t.setReceiverVpa(RECEIVER);
        t.setNonce("nonce-" + packetId);
        t.setCurrency("USD");
        t.setAmount(new BigDecimal(amount));
        t.setSignedAt(Instant.now());
        t.setSynchronizedAt(Instant.now());
        t.setBridgeNodeId("test");
        t.setHopCount(0);
        t.setStatus(Transaction.Status.SETTLEMENT_REQUESTED);
        return transactions.saveAndFlush(t);
    }

    private BigDecimal balance(String vpa) {
        return accounts.findByVpa(vpa).orElseThrow().getBalance();
    }

    @Test
    void settlementDebitsAndCreditsAtomicallyWithLedger() {
        Transaction t = paymentAwaitingSettlement("pay-1", "100.00");
        settlementService.markPaymentSettled(t.getPacketId(), t.getPacketHash(), SENDER, RECEIVER,
                t.getAmount(), "test-correlation");

        assertEquals(0, new BigDecimal("49900.00").compareTo(balance(SENDER)));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(RECEIVER)));
        assertEquals(Transaction.Status.SETTLED, transactions.findById(t.getId()).orElseThrow().getStatus());

        List<LedgerEntry> entries = ledger.findByPacketHash(t.getPacketHash());
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.getDirection() == LedgerEntry.Direction.DEBIT
                && e.getAccountVpa().equals(SENDER) && e.getKind() == LedgerEntry.Kind.SETTLEMENT));
        assertTrue(entries.stream().anyMatch(e -> e.getDirection() == LedgerEntry.Direction.CREDIT
                && e.getAccountVpa().equals(RECEIVER) && e.getKind() == LedgerEntry.Kind.SETTLEMENT));
    }

    @Test
    void repeatedSettlementDoesNotMoveValueTwice() {
        Transaction t = paymentAwaitingSettlement("pay-2", "100.00");
        settlementService.markPaymentSettled(t.getPacketId(), t.getPacketHash(), SENDER, RECEIVER,
                t.getAmount(), "c1");
        settlementService.markPaymentSettled(t.getPacketId(), t.getPacketHash(), SENDER, RECEIVER,
                t.getAmount(), "c2");

        assertEquals(0, new BigDecimal("49900.00").compareTo(balance(SENDER)));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(RECEIVER)));
        assertEquals(2, ledger.findByPacketHash(t.getPacketHash()).size());
    }

    @Test
    void concurrentSettlementsFromSameSenderStayConsistent() throws Exception {
        Transaction a = paymentAwaitingSettlement("pay-conc-a", "100.00");
        Transaction b = paymentAwaitingSettlement("pay-conc-b", "200.00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var fa = pool.submit(() -> { start.await(); settle(a); return null; });
            var fb = pool.submit(() -> { start.await(); settle(b); return null; });
            start.countDown();
            fa.get(30, TimeUnit.SECONDS);
            fb.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, new BigDecimal("49700.00").compareTo(balance(SENDER)));
        assertEquals(0, new BigDecimal("300.00").compareTo(balance(RECEIVER)));
        assertEquals(4, ledger.findByPacketHash(a.getPacketHash()).size()
                + ledger.findByPacketHash(b.getPacketHash()).size());
    }

    private void settle(Transaction t) {
        settlementService.markPaymentSettled(t.getPacketId(), t.getPacketHash(), SENDER, RECEIVER,
                t.getAmount(), "concurrent");
    }

    @Test
    void insufficientBalanceRejectsPaymentWithoutFinancialEffect() {
        Account sender = accounts.findByVpa(SENDER).orElseThrow();
        sender.setBalance(new BigDecimal("50.00"));
        accounts.save(sender);

        Transaction t = paymentAwaitingSettlement("pay-poor", "100.00");
        settlementService.markPaymentSettled(t.getPacketId(), t.getPacketHash(), SENDER, RECEIVER,
                t.getAmount(), "c1");

        assertEquals(Transaction.Status.REJECTED, transactions.findById(t.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(balance(SENDER)));
        assertTrue(ledger.findByPacketHash(t.getPacketHash()).isEmpty());
    }

    @Test
    void fullRefundRestoresBalancesWithCompensatingEntries() {
        Transaction t = paymentAwaitingSettlement("pay-refund", "100.00");
        settlementService.markPaymentSettled(t.getPacketId(), t.getPacketHash(), SENDER, RECEIVER,
                t.getAmount(), "c1");

        Refund refund = refundService.refund(t.getPacketId(), "customer request", "ops-1");
        assertEquals(Transaction.Status.REFUNDED, transactions.findById(t.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("50000.00").compareTo(balance(SENDER)));
        assertEquals(0, BigDecimal.ZERO.compareTo(balance(RECEIVER)));

        List<LedgerEntry> entries = ledger.findByPacketHash(t.getPacketHash());
        assertEquals(4, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.getKind() == LedgerEntry.Kind.REFUND
                && e.getDirection() == LedgerEntry.Direction.DEBIT && e.getAccountVpa().equals(RECEIVER)));
        assertTrue(entries.stream().anyMatch(e -> e.getKind() == LedgerEntry.Kind.REFUND
                && e.getDirection() == LedgerEntry.Direction.CREDIT && e.getAccountVpa().equals(SENDER)));

        // Idempotent: second refund returns the same record, no new entries.
        Refund again = refundService.refund(t.getPacketId(), "duplicate", "ops-2");
        assertEquals(refund.getId(), again.getId());
        assertEquals(4, ledger.findByPacketHash(t.getPacketHash()).size());
    }

    @Test
    void refundOfUnsettledPaymentIsRejected() {
        Transaction t = paymentAwaitingSettlement("pay-unsettled-refund", "100.00");
        var ex = assertThrows(RefundService.RefundException.class,
                () -> refundService.refund(t.getPacketId(), "too early", "ops"));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void deadLetterReplayResetsOutboxState() {
        Outbox entry = Outbox.builder()
                .eventId("evt-dlt-1").eventType("PAYMENT_SETTLED").aggregateId("hash-x")
                .payload("{}").topic("payment-settled").partitionKey("hash-x")
                .processed(true).deadLetter(true).retryCount(10)
                .errorMessage("RuntimeException: kafka down")
                .createdAt(Instant.now()).processedAt(Instant.now())
                .nextAttemptAt(Instant.now()).build();
        Outbox saved = outbox.saveAndFlush(entry);

        Outbox replayed = recoveryService.replay(saved.getId(), "ops-1");
        assertFalse(replayed.getProcessed());
        assertFalse(replayed.getDeadLetter());
        assertEquals(0, replayed.getRetryCount());
        assertEquals(1, replayed.getReplayCount());
        assertEquals("ops-1", replayed.getLastReplayedBy());
        assertNotNull(replayed.getLastReplayedAt());

        // Cannot replay a record that is no longer dead-lettered.
        assertThrows(OutboxRecoveryService.ReplayException.class,
                () -> recoveryService.replay(saved.getId(), "ops-1"));
    }

    @Test
    void replayRejectsNonDeadLetterAndMissingRecords() {
        Outbox pending = Outbox.builder()
                .eventId("evt-pending").eventType("X").aggregateId("a")
                .payload("{}").topic("t").partitionKey("a")
                .processed(false).deadLetter(false)
                .createdAt(Instant.now()).nextAttemptAt(Instant.now()).build();
        Outbox saved = outbox.saveAndFlush(pending);
        var ex = assertThrows(OutboxRecoveryService.ReplayException.class,
                () -> recoveryService.replay(saved.getId(), "ops"));
        assertEquals(409, ex.getStatus());
        assertThrows(OutboxRecoveryService.ReplayException.class,
                () -> recoveryService.replay(999999L, "ops"));
    }
}
