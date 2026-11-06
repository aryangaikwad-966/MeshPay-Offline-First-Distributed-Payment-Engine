package com.demo.meshpay.saga.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Command to request settlement for a payment
 * Sent from Saga Service to Payment Service via Kafka
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestSettlementCommand {
    private String sagaId;
    private String packetId;
    private String packetHash;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String nonce;
    private Long signedAt;
}
