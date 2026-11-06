package com.demo.meshpay.payment.controller;

import com.demo.meshpay.payment.api.PacketSyncResponse;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.demo.meshpay.payment.security.PacketCryptography;
import com.demo.meshpay.payment.service.PaymentPacketSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payment/packets")
@RequiredArgsConstructor
@Tag(name = "Offline payment packets")
public class PacketController {
    private final PaymentPacketSyncService syncService;
    private final PacketCryptography cryptography;

    @PostMapping("/sync")
    @Operation(summary = "Synchronize an offline-created encrypted payment packet")
    @RateLimiter(name = "packetSync")
    public ResponseEntity<PacketSyncResponse> synchronize(@Valid @RequestBody PaymentPacketRequest packet) {
        PacketSyncResponse response = syncService.synchronize(packet);
        return switch (response.status()) {
            case "SYNCHRONIZED" -> ResponseEntity.status(201).body(response);
            case "IN_PROGRESS" -> ResponseEntity.accepted().header("Retry-After", "2").body(response);
            default -> ResponseEntity.ok(response);
        };
    }

    @GetMapping("/recipient-key")
    @Operation(summary = "Get the public RSA key used to wrap offline packet AES keys")
    public Map<String, String> recipientKey() {
        return Map.of("algorithm", "RSA-OAEP-SHA256-MGF1-SHA256", "publicKey", cryptography.recipientPublicKey());
    }
}
