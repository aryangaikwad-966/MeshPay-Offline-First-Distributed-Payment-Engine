package com.demo.meshpay.payment.controller;

import com.demo.meshpay.payment.model.LedgerEntry;
import com.demo.meshpay.payment.model.Refund;
import com.demo.meshpay.payment.repository.LedgerEntryRepository;
import com.demo.meshpay.payment.repository.RefundRepository;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.demo.meshpay.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Full-refund and ledger inspection endpoints. In the secured profile these
 * require an authenticated principal; refund requests are recorded with the
 * caller identity when a JWT is present.
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Refunds & Ledger", description = "Simulated-balance refunds and immutable ledger inspection")
public class RefundController {

    private final RefundService refundService;
    private final RefundRepository refunds;
    private final LedgerEntryRepository ledgerEntries;
    private final TransactionRepository transactions;

    public record RefundRequest(String reason) {}

    @Operation(summary = "Refund a settled payment in full (idempotent)")
    @PostMapping("/payments/{identifier}/refund")
    public ResponseEntity<?> refund(@PathVariable String identifier,
                                    @RequestBody(required = false) RefundRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        String requestedBy = jwt != null ? jwt.getSubject() : "anonymous";
        try {
            Refund refund = refundService.refund(identifier,
                    request == null ? null : request.reason(), requestedBy);
            return ResponseEntity.ok(Map.of(
                    "refundId", refund.getId(),
                    "packetHash", refund.getPacketHash(),
                    "amount", refund.getAmount(),
                    "currency", refund.getCurrency(),
                    "status", refund.getStatus(),
                    "createdAt", refund.getCreatedAt()));
        } catch (RefundService.RefundException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "List recent refunds")
    @GetMapping("/refunds")
    public List<Refund> listRefunds() {
        return refunds.findTop100ByOrderByCreatedAtDesc();
    }

    @Operation(summary = "Immutable ledger entries for a payment")
    @GetMapping("/payments/{identifier}/ledger")
    public ResponseEntity<?> ledger(@PathVariable String identifier) {
        var packetHash = transactions.findByPacketId(identifier)
                .or(() -> transactions.findByPacketHash(identifier))
                .map(t -> t.getPacketHash())
                .orElse(identifier);
        List<LedgerEntry> entries = ledgerEntries.findByPacketHash(packetHash);
        if (entries.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entries);
    }
}
