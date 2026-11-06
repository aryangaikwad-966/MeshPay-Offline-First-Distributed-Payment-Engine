package com.demo.meshpay.saga.controller;

import com.demo.meshpay.saga.model.SagaState;
import com.demo.meshpay.saga.service.PaymentSagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Saga Service", description = "Saga orchestration endpoints")
public class SagaController {

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    @Operation(summary = "Start a new payment saga")
    @PostMapping("/start")
    public ResponseEntity<?> startSaga(@RequestBody StartSagaRequest request) {
        try {
            String sagaId = paymentSagaOrchestrator.startPaymentSaga(
                request.packetId,
                request.packetHash,
                request.bridgeNodeId,
                request.ciphertext
            );
            return ResponseEntity.ok(Map.of("sagaId", sagaId, "status", "STARTED"));
        } catch (Exception e) {
            log.error("Failed to start saga", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Validate payment in saga")
    @PostMapping("/validate")
    public ResponseEntity<?> validatePayment(@RequestBody ValidateSagaRequest request) {
        try {
            paymentSagaOrchestrator.validatePaymentSaga(
                request.sagaId,
                request.packetId,
                request.packetHash,
                request.senderVpa,
                request.receiverVpa,
                request.amount,
                request.nonce,
                request.signedAt
            );
            return ResponseEntity.ok(Map.of("status", "VALIDATION_INITIATED"));
        } catch (Exception e) {
            log.error("Failed to validate payment in saga", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Request settlement in saga")
    @PostMapping("/settlement/request")
    public ResponseEntity<?> requestSettlement(@RequestBody ValidateSagaRequest request) {
        try {
            paymentSagaOrchestrator.requestSettlementSaga(
                request.sagaId,
                request.packetId,
                request.packetHash,
                request.senderVpa,
                request.receiverVpa,
                request.amount,
                request.nonce,
                request.signedAt
            );
            return ResponseEntity.ok(Map.of("status", "SETTLEMENT_REQUESTED"));
        } catch (Exception e) {
            log.error("Failed to request settlement in saga", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Complete settlement in saga")
    @PostMapping("/settlement/complete")
    public ResponseEntity<?> completeSettlement(@RequestBody CompleteSagaRequest request) {
        try {
            paymentSagaOrchestrator.completePaymentSaga(
                request.sagaId,
                request.packetId,
                request.packetHash,
                request.senderVpa,
                request.receiverVpa,
                request.amount
            );
            return ResponseEntity.ok(Map.of("status", "SETTLEMENT_COMPLETED"));
        } catch (Exception e) {
            log.error("Failed to complete settlement in saga", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get saga status")
    @GetMapping("/status/{sagaId}")
    public ResponseEntity<SagaState> getSagaStatus(@PathVariable String sagaId) {
        SagaState state = paymentSagaOrchestrator.getSagaStatus(sagaId);
        if (state != null) {
            return ResponseEntity.ok(state);
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "Health check")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "saga-service"));
    }

    // Request DTOs
    public static class StartSagaRequest {
        public String packetId;
        public String packetHash;
        public String bridgeNodeId;
        public String ciphertext;
    }

    public static class ValidateSagaRequest {
        public String sagaId;
        public String packetId;
        public String packetHash;
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String nonce;
        public Long signedAt;
    }

    public static class CompleteSagaRequest {
        public String sagaId;
        public String packetId;
        public String packetHash;
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
    }
}
