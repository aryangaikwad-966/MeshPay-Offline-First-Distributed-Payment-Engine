package com.demo.meshpay.payment.controller;

import com.demo.meshpay.payment.model.Outbox;
import com.demo.meshpay.payment.repository.OutboxRepository;
import com.demo.meshpay.payment.service.OutboxRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Dead-letter queue inspection and controlled replay for the transactional
 * outbox. Requires the meshpay:ops scope outside the dev profile.
 * Payloads are never exposed — only metadata needed for investigation.
 */
@RestController
@RequestMapping("/api/payment/ops/outbox")
@RequiredArgsConstructor
@Tag(name = "Outbox Recovery", description = "Dead-letter inspection and replay")
public class OutboxRecoveryController {

    private final OutboxRepository outbox;
    private final OutboxRecoveryService recoveryService;

    @Operation(summary = "List dead-lettered outbox records (published to .DLT topics)")
    @GetMapping("/dead")
    public Page<DeadLetterView> deadLetters(
            @RequestParam(required = false) String topic,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Outbox> page = topic == null || topic.isBlank()
                ? outbox.findByDeadLetterTrue(pageable)
                : outbox.findByDeadLetterTrueAndTopicContainingIgnoreCase(topic, pageable);
        return page.map(OutboxRecoveryController::view);
    }

    @Operation(summary = "Replay a dead-lettered event back to its original topic")
    @PostMapping("/replay/{id}")
    public ResponseEntity<?> replay(@PathVariable Long id,
                                    @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt != null ? jwt.getSubject() : "anonymous";
        try {
            Outbox replayed = recoveryService.replay(id, actor);
            return ResponseEntity.ok(Map.of(
                    "id", replayed.getId(),
                    "eventId", replayed.getEventId(),
                    "topic", replayed.getTopic(),
                    "state", "QUEUED_FOR_REPUBLISH",
                    "replayCount", replayed.getReplayCount(),
                    "lastReplayedBy", replayed.getLastReplayedBy(),
                    "lastReplayedAt", replayed.getLastReplayedAt()));
        } catch (OutboxRecoveryService.ReplayException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
    }

    private static DeadLetterView view(Outbox o) {
        return new DeadLetterView(o.getId(), o.getEventId(), o.getEventType(), o.getTopic(),
                o.getRetryCount(), o.getErrorMessage(), o.getCreatedAt(), o.getProcessedAt(),
                o.getLastReplayedBy(), o.getLastReplayedAt(), o.getReplayCount());
    }

    public record DeadLetterView(Long id, String eventId, String eventType, String topic,
                                 Integer retryCount, String errorMessage, Instant createdAt,
                                 Instant processedAt, String lastReplayedBy,
                                 Instant lastReplayedAt, Integer replayCount) {}
}
