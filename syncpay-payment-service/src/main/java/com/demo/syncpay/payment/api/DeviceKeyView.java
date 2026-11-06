package com.demo.syncpay.payment.api;

import java.time.Instant;

public record DeviceKeyView(String deviceId, String status, Instant registeredAt, Instant revokedAt) {}
