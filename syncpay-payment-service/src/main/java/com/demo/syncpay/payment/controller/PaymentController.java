package com.demo.meshpay.payment.controller;

import com.demo.meshpay.payment.model.Account;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.AccountRepository;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.demo.meshpay.payment.service.PaymentSettlementService;
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
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Service", description = "Payment processing and settlement endpoints")
public class PaymentController {

    private final PaymentSettlementService paymentSettlementService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Operation(summary = "Validate a payment")
    @PostMapping("/validate")
    public ResponseEntity<?> validatePayment(@RequestBody ValidatePaymentRequest request) {
        try {
            paymentSettlementService.validatePayment(
                request.packetId,
                request.packetHash,
                request.senderVpa,
                request.receiverVpa,
                request.amount,
                request.nonce,
                request.signedAt,
                request.parentEventId
            );
            return ResponseEntity.ok(Map.of("status", "validation initiated"));
        } catch (Exception e) {
            log.error("Payment validation failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Request settlement for a payment")
    @PostMapping("/settlement/request")
    public ResponseEntity<?> requestSettlement(@RequestBody SettlementRequest request) {
        try {
            paymentSettlementService.requestSettlement(
                request.packetId,
                request.packetHash,
                request.senderVpa,
                request.receiverVpa,
                request.amount,
                request.nonce,
                request.signedAt,
                request.parentEventId
            );
            return ResponseEntity.ok(Map.of("status", "settlement requested"));
        } catch (Exception e) {
            log.error("Settlement request failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Mark payment as settled")
    @PostMapping("/settlement/complete")
    public ResponseEntity<?> completeSettlement(@RequestBody CompleteSettlementRequest request) {
        try {
            paymentSettlementService.markPaymentSettled(
                request.packetId,
                request.packetHash,
                request.senderVpa,
                request.receiverVpa,
                request.amount,
                request.parentEventId
            );
            return ResponseEntity.ok(Map.of("status", "settlement completed"));
        } catch (Exception e) {
            log.error("Settlement completion failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Handle payment failure")
    @PostMapping("/failure")
    public ResponseEntity<?> handleFailure(@RequestBody FailureRequest request) {
        try {
            paymentSettlementService.handlePaymentFailure(
                request.packetId,
                request.packetHash,
                request.failureReason,
                request.failureDetails,
                request.parentEventId
            );
            return ResponseEntity.ok(Map.of("status", "failure recorded"));
        } catch (Exception e) {
            log.error("Failure handling failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get all accounts")
    @GetMapping("/accounts")
    public ResponseEntity<List<Account>> getAccounts() {
        return ResponseEntity.ok(accountRepository.findAll());
    }

    @Operation(summary = "Get recent transactions")
    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions() {
        return ResponseEntity.ok(transactionRepository.findTop20ByOrderByIdDesc());
    }

    @Operation(summary = "Health check")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "payment-service"));
    }

    // Request DTOs
    public static class ValidatePaymentRequest {
        public String packetId;
        public String packetHash;
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String nonce;
        public Long signedAt;
        public String parentEventId;
    }

    public static class SettlementRequest {
        public String packetId;
        public String packetHash;
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String nonce;
        public Long signedAt;
        public String parentEventId;
    }

    public static class CompleteSettlementRequest {
        public String packetId;
        public String packetHash;
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String parentEventId;
    }

    public static class FailureRequest {
        public String packetId;
        public String packetHash;
        public String failureReason;
        public String failureDetails;
        public String parentEventId;
    }
}
