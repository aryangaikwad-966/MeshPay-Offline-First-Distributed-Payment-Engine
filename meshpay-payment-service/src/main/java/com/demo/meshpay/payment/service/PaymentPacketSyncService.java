package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.api.PacketSyncResponse;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.demo.meshpay.payment.metrics.PacketMetrics;
import com.demo.meshpay.payment.model.PacketAttempt;
import com.demo.meshpay.payment.model.Transaction;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.demo.meshpay.payment.security.PacketCryptography;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor @Slf4j
public class PaymentPacketSyncService {
    private static final Duration CLAIM_TTL = Duration.ofSeconds(60);
    private static final Duration COMPLETED_TTL = Duration.ofDays(30);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[1], 'COMMITTED', 'EX', ARGV[2]); return 1 else return 0 end", Long.class);

    private final PacketCryptography cryptography;
    private final StringRedisTemplate redis;
    private final TransactionRepository transactions;
    private final PacketPersistenceService persistence;
    private final PacketAttemptRecorder attempts;
    private final PacketMetrics metrics;

    public PacketSyncResponse synchronize(PaymentPacketRequest envelope) {
        PacketCryptography.VerifiedPacket verified;
        try { verified = cryptography.verifyAndDecrypt(envelope); }
        catch (PacketCryptography.PacketException e) {
            attempts.record(null, envelope.senderVpa().toLowerCase(), PacketAttempt.Outcome.REJECTED, e.getCode());
            metrics.rejected();
            throw e;
        }

        String sender = verified.payment().senderVpa().toLowerCase();
        Transaction existing = transactions.findByPacketHash(verified.packetHash()).orElse(null);
        if (existing != null) return duplicate(envelope, verified.packetHash());
        existing = transactions.findBySenderVpaAndNonce(sender, verified.payment().senderNonce()).orElse(null);
        if (existing != null) return replayOrDuplicate(envelope, verified.packetHash(), existing);

        String key = "meshpay:idempotency:" + verified.packetHash();
        String owner = UUID.randomUUID().toString();
        boolean ownsClaim = false;
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(key, owner, CLAIM_TTL);
            ownsClaim = Boolean.TRUE.equals(claimed);
            if (Boolean.FALSE.equals(claimed)) {
                existing = transactions.findByPacketHash(verified.packetHash()).orElse(null);
                if (existing != null) return duplicate(envelope, verified.packetHash());
                attempts.record(verified.packetHash(), sender, PacketAttempt.Outcome.IN_PROGRESS, "SYNC_IN_PROGRESS");
                return new PacketSyncResponse(envelope.packetId(), verified.packetHash(), "IN_PROGRESS",
                        "Synchronization is in progress; retry with the same packet shortly");
            }
            if (claimed == null) metrics.redisFallback();
        } catch (DataAccessException e) {
            metrics.redisFallback();
            log.warn("Redis claim unavailable; relying on PostgreSQL uniqueness constraints");
        }

        try {
            persistence.synchronize(envelope, verified.payment(), verified.packetHash());
            if (ownsClaim) {
                try { redis.execute(COMPLETE, List.of(key), owner, Long.toString(COMPLETED_TTL.toSeconds())); }
                catch (DataAccessException e) { log.warn("Could not cache committed claim; durable PostgreSQL row is authoritative"); }
            }
            attempts.record(verified.packetHash(), sender, PacketAttempt.Outcome.ACCEPTED, "DURABLY_SYNCHRONIZED");
            metrics.accepted();
            return new PacketSyncResponse(envelope.packetId(), verified.packetHash(), "SYNCHRONIZED",
                    "Packet is durably synchronized; validation and settlement are separate lifecycle steps");
        } catch (DataIntegrityViolationException e) {
            existing = transactions.findByPacketHash(verified.packetHash()).orElse(null);
            if (existing != null) return duplicate(envelope, verified.packetHash());
            existing = transactions.findBySenderVpaAndNonce(sender, verified.payment().senderNonce()).orElse(null);
            if (existing != null) return replayOrDuplicate(envelope, verified.packetHash(), existing);
            release(key, owner, ownsClaim);
            throw e;
        } catch (RuntimeException e) {
            release(key, owner, ownsClaim);
            throw e;
        }
    }

    private PacketSyncResponse duplicate(PaymentPacketRequest packet, String hash) {
        attempts.record(hash, packet.senderVpa().toLowerCase(), PacketAttempt.Outcome.DUPLICATE, "PACKET_ALREADY_SYNCHRONIZED");
        metrics.duplicate();
        return new PacketSyncResponse(packet.packetId(), hash, "DUPLICATE", "Packet was already durably synchronized");
    }

    private PacketSyncResponse replayOrDuplicate(PaymentPacketRequest packet, String hash, Transaction existing) {
        if (existing.getPacketHash().equals(hash)) return duplicate(packet, hash);
        attempts.record(hash, packet.senderVpa().toLowerCase(), PacketAttempt.Outcome.REJECTED, "NONCE_REPLAY");
        metrics.replay();
        throw new PacketSyncException("NONCE_REPLAY", "Sender nonce was already used by a different payment", 409);
    }

    private void release(String key, String owner, boolean ownsClaim) {
        if (!ownsClaim) return;
        try { redis.execute(RELEASE, List.of(key), owner); }
        catch (DataAccessException e) { log.warn("Could not release failed Redis claim; it will expire"); }
    }

    public static class PacketSyncException extends RuntimeException {
        private final String code; private final int status;
        public PacketSyncException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
        public String getCode() { return code; } public int getStatus() { return status; }
    }
}
