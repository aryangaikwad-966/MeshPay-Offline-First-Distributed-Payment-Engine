package com.demo.syncpay.payment.api;

public record PacketSyncResponse(String packetId, String packetHash, String status, String message) {}
