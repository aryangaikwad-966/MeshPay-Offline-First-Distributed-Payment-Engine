package com.demo.meshpay.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Encrypted packet envelope created offline and synchronized later. */
public record PaymentPacketRequest(
        @NotBlank @Size(max = 80) String packetId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}@[A-Za-z0-9.-]{1,64}") String senderVpa,
        @NotBlank @Size(max = 128) String senderNonce,
        @NotBlank @Size(max = 32) String iv,
        @NotBlank @Size(max = 1024) String wrappedAesKey,
        @NotBlank @Size(max = 131072) String ciphertext,
        @NotBlank @Size(max = 1024) String signature,
        @NotBlank @Size(max = 80) String bridgeNodeId,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1000) int hopCount
) {}
