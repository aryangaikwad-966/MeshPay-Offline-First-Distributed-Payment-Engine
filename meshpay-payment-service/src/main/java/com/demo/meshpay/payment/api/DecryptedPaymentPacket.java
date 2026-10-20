package com.demo.meshpay.payment.api;

import java.math.BigDecimal;

public record DecryptedPaymentPacket(String packetId, String senderVpa, String receiverVpa,
        BigDecimal amount, String currency, long signedAt, String senderNonce) {}
