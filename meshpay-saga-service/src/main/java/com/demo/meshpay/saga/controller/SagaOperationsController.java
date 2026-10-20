package com.demo.meshpay.saga.controller;

import com.demo.meshpay.saga.model.SagaState;
import com.demo.meshpay.saga.model.SagaTransition;
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
    public record Overview(long total, long completed, long compensated, long active) {}
    public record SagaView(String sagaId, String packetHash, String packetId, String state, String currentStep,
                           String failureReason, Instant createdAt, Instant updatedAt) {}
    public record TransitionView(String fromState, String toState, String reason, Instant changedAt) {}
}
