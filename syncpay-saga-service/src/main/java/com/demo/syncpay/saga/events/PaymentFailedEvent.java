package com.demo.meshpay.saga.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String packetId;
    private String packetHash;
    private String failureReason;
    private String failureDetails;
    private Instant failedAt;
}
