package com.demo.meshpay.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event emitted when a payment fails
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {

    private String packetId;
    private String packetHash;
    private String failureType;
    private String failureReason;
    private Instant failedAt;
}
