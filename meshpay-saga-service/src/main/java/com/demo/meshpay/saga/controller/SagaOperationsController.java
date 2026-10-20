package com.demo.meshpay.saga.controller;

import com.demo.meshpay.saga.model.SagaState;
import com.demo.meshpay.saga.model.SagaTransition;
import com.demo.meshpay.saga.model.SagaCommandOutbox;
import com.demo.meshpay.saga.repository.SagaCommandOutboxRepository;
import com.demo.meshpay.saga.repository.SagaStateRepository;
import com.demo.meshpay.saga.repository.SagaTransitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/saga/ops")
@RequiredArgsConstructor
public class SagaOperationsController {
    private static final List<String> TERMINAL = List.of("SAGA_COMPLETED", "SAGA_COMPENSATED", "SAGA_START_FAILED");
    private final SagaStateRepository states;
    private final SagaTransitionRepository transitions;
    private final SagaCommandOutboxRepository outbox;

    @GetMapping("/overview")
    @Operation(summary = "Current persisted Saga counts")
    public Overview overview() {
        return new Overview(states.count(), states.countByState("SAGA_COMPLETED"),
                states.countByState("SAGA_COMPENSATED"), states.countByStateNotIn(TERMINAL));
    }

    @GetMapping("/sagas")
    @Operation(summary = "Recent persisted Saga states")
    public List<SagaView> sagas() {
        return states.findTop100ByOrderByUpdatedAtDesc().stream().map(SagaOperationsController::view).toList();
    }

    @GetMapping("/outbox")
    @Operation(summary = "Recent Saga command publication status without command payloads")
    public List<OutboxView> outbox() {
        return outbox.findTop100ByOrderByCreatedAtDesc().stream().map(SagaOperationsController::view).toList();
    }

    @GetMapping("/outbox/overview")
    @Operation(summary = "Saga command outbox backlog counts")
    public OutboxOverview outboxOverview() {
        return new OutboxOverview(outbox.countByState("PENDING"), outbox.countByState("PUBLISHED"), outbox.countByState("DEAD"));
    }

    @GetMapping("/sagas/{sagaId}/history")
    @Operation(summary = "Persisted transition history for one Saga")
    public ResponseEntity<List<TransitionView>> history(@PathVariable String sagaId) {
        if (states.findBySagaId(sagaId).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(transitions.findBySagaIdOrderByChangedAtAsc(sagaId).stream()
                .map(t -> new TransitionView(t.getFromState(), t.getToState(), t.getReason(), t.getChangedAt())).toList());
    }

    private static SagaView view(SagaState state) {
        return new SagaView(state.getSagaId(), state.getPacketHash(), state.getPacketId(), state.getState(),
                state.getCurrentStep(), state.getFailureReason(), state.getCreatedAt(), state.getUpdatedAt());
    }
    private static OutboxView view(SagaCommandOutbox row) {
        String error = row.getLastError();
        String errorCode = error == null ? null : error.substring(0, error.indexOf(':') > 0 ? error.indexOf(':') : error.length());
        return new OutboxView(row.getCommandId(), row.getSagaId(), row.getTopic(), row.getState(), row.getAttempts(),
                row.getCreatedAt(), row.getPublishedAt(), errorCode);
    }
    public record Overview(long total, long completed, long compensated, long active) {}
    public record SagaView(String sagaId, String packetHash, String packetId, String state, String currentStep,
                           String failureReason, Instant createdAt, Instant updatedAt) {}
    public record TransitionView(String fromState, String toState, String reason, Instant changedAt) {}
    public record OutboxOverview(long pending, long published, long dead) {}
    public record OutboxView(String commandId, String sagaId, String topic, String state, int attempts,
                             Instant createdAt, Instant publishedAt, String errorCode) {}
}
