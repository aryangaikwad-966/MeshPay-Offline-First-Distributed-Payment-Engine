package com.demo.meshpay.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event emitted when a payment is settled
 */
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
