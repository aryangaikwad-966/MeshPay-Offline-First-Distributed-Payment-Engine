package com.demo.meshpay.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
public class FallbackController {
    @GetMapping("/fallback/payment")
    public Mono<ResponseEntity<Map<String, String>>> paymentUnavailable() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "PAYMENT_SERVICE_UNAVAILABLE", "message", "Payment service is temporarily unavailable")));
    }
    @GetMapping("/fallback/saga")
    public Mono<ResponseEntity<Map<String, String>>> sagaUnavailable() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "SAGA_SERVICE_UNAVAILABLE", "message", "Saga service is temporarily unavailable")));
    }
}
