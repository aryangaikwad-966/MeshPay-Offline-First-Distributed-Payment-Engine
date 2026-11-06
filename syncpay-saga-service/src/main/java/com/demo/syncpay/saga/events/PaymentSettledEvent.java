package com.demo.meshpay.saga.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSettledEvent {
    private String packetId;
    private String packetHash;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private Instant settledAt;
}
