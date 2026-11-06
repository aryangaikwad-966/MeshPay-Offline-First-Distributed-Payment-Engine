package com.demo.syncpay.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public signing key registered by a customer device for a sender VPA. */
public record DeviceRegistrationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}@[A-Za-z0-9.-]{1,64}") String vpa,
        @NotBlank @Size(max = 64) String deviceId,
        @NotBlank @Size(max = 2048) String publicKey
) {}
