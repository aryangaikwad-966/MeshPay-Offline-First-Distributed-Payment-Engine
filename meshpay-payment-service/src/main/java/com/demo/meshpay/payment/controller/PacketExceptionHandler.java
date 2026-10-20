package com.demo.meshpay.payment.controller;

import com.demo.meshpay.payment.security.PacketCryptography;
import com.demo.meshpay.payment.service.PaymentPacketSyncService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class PacketExceptionHandler {
    @ExceptionHandler(PacketCryptography.PacketException.class)
    public ResponseEntity<?> cryptography(PacketCryptography.PacketException e) {
        return ResponseEntity.status(e.getStatus()).body(error(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler(PaymentPacketSyncService.PacketSyncException.class)
    public ResponseEntity<?> synchronization(PaymentPacketSyncService.PacketSyncException e) {
        return ResponseEntity.status(e.getStatus()).body(error(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalidRequest(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(error("INVALID_PACKET_FIELDS", "Packet request fields are missing or invalid"));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> persistenceUnavailable(DataAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("PERSISTENCE_UNAVAILABLE", "Packet was not confirmed; retry with the same packet"));
    }
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<?> rateLimited(RequestNotPermitted e) {
        return ResponseEntity.status(429).header("Retry-After", "1")
                .body(error("RATE_LIMITED", "Packet synchronization request rate is limited"));
    }
    private Map<String, Object> error(String code, String message) {
        return Map.of("code", code, "message", message, "timestamp", Instant.now().toString());
    }
}
