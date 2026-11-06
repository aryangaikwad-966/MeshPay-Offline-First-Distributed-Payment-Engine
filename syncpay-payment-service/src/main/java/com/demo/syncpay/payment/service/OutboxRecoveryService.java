package com.demo.syncpay.payment.service;

import com.demo.syncpay.payment.model.Outbox;
import com.demo.syncpay.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Controlled dead-letter replay for the transactional outbox.
 *
 * Lifecycle: the OutboxProcessor publishes each row to its topic; after
 * maxRetries failures it publishes once to "{topic}.DLT" and marks the row
 * processed=true, deadLetter=true. Replay resets the row so the processor
 * republishes the SAME payload to the ORIGINAL topic — the original eventId
 * and business identity are preserved, so downstream consumer deduplication
 * (processed_commands / saga ProcessedEvent) still applies.
 */
@Service
@RequiredArgsConstructor
public class OutboxRecoveryService {

    private final OutboxRepository outboxRepository;

    public static class ReplayException extends RuntimeException {
        private final int status;
        public ReplayException(String message, int status) { super(message); this.status = status; }
        public int getStatus() { return status; }
    }

    @Transactional
    public Outbox replay(Long id, String actor) {
        Outbox entry = outboxRepository.findById(id)
                .orElseThrow(() -> new ReplayException("Outbox record not found: " + id, 404));

        if (!Boolean.TRUE.equals(entry.getDeadLetter()))
            throw new ReplayException("Record " + id + " is not dead-lettered (deadLetter="
                    + entry.getDeadLetter() + ")", 409);
        if (!Boolean.TRUE.equals(entry.getProcessed()))
            throw new ReplayException("Record " + id + " has not completed dead-letter publication", 409);

        entry.setProcessed(false);
        entry.setProcessedAt(null);
        entry.setDeadLetter(false);
        entry.setRetryCount(0);
        entry.setErrorMessage(null);
        entry.setNextAttemptAt(Instant.now());
        entry.setReplayCount((entry.getReplayCount() == null ? 0 : entry.getReplayCount()) + 1);
        entry.setLastReplayedBy(actor);
        entry.setLastReplayedAt(Instant.now());
        return outboxRepository.save(entry);
    }
}
