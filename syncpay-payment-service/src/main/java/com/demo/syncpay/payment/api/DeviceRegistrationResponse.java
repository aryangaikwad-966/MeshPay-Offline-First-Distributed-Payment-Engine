package com.demo.syncpay.payment.api;

import java.time.Instant;

public record DeviceRegistrationResponse(String vpa, String deviceId, String status, Instant registeredAt) {}
