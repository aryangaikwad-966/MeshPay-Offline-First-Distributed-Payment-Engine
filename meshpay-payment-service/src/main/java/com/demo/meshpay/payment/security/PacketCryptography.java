package com.demo.meshpay.payment.security;

import com.demo.meshpay.payment.api.DecryptedPaymentPacket;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PacketCryptography {
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final PSSParameterSpec PSS = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
    private final ObjectMapper mapper;
    private final byte[] privateKeyBytes;
    private final String publicKeyBase64;
    private final Map<String, String> trustedSigners;
    private final Duration maxAge;

    public PacketCryptography(ObjectMapper mapper,
            @Value("${meshpay.packet.server-private-key:}") String privateKey,
            @Value("${meshpay.packet.server-public-key:}") String publicKey,
            @Value("${meshpay.packet.trusted-signers:}") String trustedSigners,
            @Value("${meshpay.packet.max-offline-age:30d}") Duration maxAge) {
        this.mapper = mapper;
        this.privateKeyBytes = privateKey == null || privateKey.isBlank() ? new byte[0] : decode(privateKey, "private key");
        this.publicKeyBase64 = publicKey == null ? "" : publicKey;
        this.trustedSigners = Arrays.stream(trustedSigners.split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0].toLowerCase(), parts -> parts[1], (a, b) -> a));
        this.maxAge = maxAge;
    }

    @PostConstruct
    void validateRecipientKeyPair() {
        boolean hasPrivate = privateKeyBytes.length > 0;
        boolean hasPublic = !publicKeyBase64.isBlank();
        if (hasPrivate != hasPublic)
            throw new IllegalStateException("Configure both MESHPAY_PACKET_RSA_PRIVATE_KEY and MESHPAY_PACKET_RSA_PUBLIC_KEY, or neither");
        if (!hasPrivate) return; // local UI and other APIs can run without packet sync enabled
        try {
            var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            var publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decode(publicKeyBase64, "recipient public key")));
            if (!(privateKey instanceof RSAPrivateKey privateRsa) || !(publicKey instanceof RSAPublicKey publicRsa)
                    || privateRsa.getModulus().bitLength() < 2048 || publicRsa.getModulus().bitLength() < 2048
                    || !privateRsa.getModulus().equals(publicRsa.getModulus()))
                throw new IllegalStateException("Recipient RSA keys must be a matching pair of at least 2048 bits");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Configured recipient RSA key pair is invalid", e);
        }
    }

    public VerifiedPacket verifyAndDecrypt(PaymentPacketRequest envelope) {
        byte[] iv = decode(envelope.iv(), "IV");
        byte[] wrappedKey = decode(envelope.wrappedAesKey(), "wrapped key");
        byte[] ciphertext = decode(envelope.ciphertext(), "ciphertext");
        byte[] signatureBytes = decode(envelope.signature(), "signature");
        if (iv.length != 12 || ciphertext.length < 16 || ciphertext.length > 64 * 1024)
            throw new PacketException("MALFORMED_PACKET", "Packet cryptographic fields have invalid sizes", 400);
        if (envelope.hopCount() < 0 || envelope.hopCount() > 1000)
            throw new PacketException("INVALID_HOP_COUNT", "Packet hop count is outside the accepted range", 400);

        try {
            String encodedSigner = trustedSigners.get(envelope.senderVpa().toLowerCase());
            if (encodedSigner == null) throw new PacketException("UNTRUSTED_SENDER", "No trusted key is configured for this sender", 422);
            var signer = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decode(encodedSigner, "trusted sender key")));
            if (!(signer instanceof RSAPublicKey rsaSigner) || rsaSigner.getModulus().bitLength() < 2048)
                throw new PacketException("WEAK_SIGNING_KEY", "Trusted sender key must be RSA 2048-bit or stronger", 422);
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(PSS);
            verifier.initVerify(signer);
            byte[] signedData = canonicalEnvelope(envelope, iv, wrappedKey, ciphertext);
            verifier.update(signedData);
            if (!verifier.verify(signatureBytes)) throw new PacketException("INVALID_SIGNATURE", "Packet signature verification failed", 422);

            if (privateKeyBytes.length == 0) throw new PacketException("DECRYPTION_KEY_UNAVAILABLE", "Packet decryption is not configured", 503);
            var decryptionKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            if (!(decryptionKey instanceof RSAPrivateKey rsaPrivate) || rsaPrivate.getModulus().bitLength() < 2048)
                throw new PacketException("INVALID_DECRYPTION_KEY", "Configured packet key must be RSA 2048-bit or stronger", 503);
            Cipher keyCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            keyCipher.init(Cipher.DECRYPT_MODE, decryptionKey, OAEP);
            byte[] aesKey = keyCipher.doFinal(wrappedKey);
            try {
                if (aesKey.length != 32) throw new PacketException("INVALID_AES_KEY", "Wrapped key is not AES-256", 422);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
                cipher.updateAAD(aad(envelope));
                byte[] plaintext = cipher.doFinal(ciphertext);
                try {
                    DecryptedPaymentPacket payment = mapper.readValue(plaintext, DecryptedPaymentPacket.class);
                    validatePayload(envelope, payment);
                    String logicalKey = String.join("\n", "meshpay-payment-v1", payment.packetId(),
                            payment.senderVpa().toLowerCase(), payment.receiverVpa().toLowerCase(),
                            payment.amount().stripTrailingZeros().toPlainString(), payment.currency(),
                            Long.toString(payment.signedAt()), payment.senderNonce());
                    byte[] hash = MessageDigest.getInstance("SHA-256").digest(logicalKey.getBytes(StandardCharsets.UTF_8));
                    return new VerifiedPacket(payment, HexFormat.of().formatHex(hash));
                } finally { Arrays.fill(plaintext, (byte) 0); }
            } finally { Arrays.fill(aesKey, (byte) 0); }
        } catch (PacketException e) {
            throw e;
        } catch (Exception e) {
            // Keep cryptographic details private: this includes bad RSA wrapping and failed GCM tags.
            throw new PacketException("PACKET_AUTHENTICATION_FAILED", "Packet authentication or decryption failed", 422, e);
        }
    }

    public String recipientPublicKey() {
        if (publicKeyBase64.isBlank()) throw new PacketException("ENCRYPTION_KEY_UNAVAILABLE", "Packet encryption is not configured", 503);
        byte[] encoded = decode(publicKeyBase64, "recipient public key");
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048)
                throw new PacketException("INVALID_ENCRYPTION_KEY", "Configured packet key must be RSA 2048-bit or stronger", 503);
            return Base64.getEncoder().encodeToString(encoded);
        } catch (PacketException e) { throw e; }
        catch (Exception e) { throw new PacketException("INVALID_ENCRYPTION_KEY", "Configured packet key is invalid", 503, e); }
    }

    private void validatePayload(PaymentPacketRequest envelope, DecryptedPaymentPacket payment) {
        if (payment == null || !envelope.packetId().equals(payment.packetId()) ||
                !envelope.senderVpa().equalsIgnoreCase(payment.senderVpa()) ||
                !envelope.senderNonce().equals(payment.senderNonce()) || payment.receiverVpa() == null ||
                !payment.receiverVpa().matches("[A-Za-z0-9._-]{1,64}@[A-Za-z0-9.-]{1,64}") ||
                payment.amount() == null || payment.amount().signum() <= 0 || payment.amount().scale() > 2 ||
                payment.amount().precision() > 19 || payment.currency() == null || !payment.currency().matches("[A-Z]{3}"))
            throw new PacketException("INVALID_PAYMENT_CONTENT", "Decrypted payment fields are invalid or do not match the envelope", 422);
        Instant signedAt = Instant.ofEpochMilli(payment.signedAt());
        Instant now = Instant.now();
        if (signedAt.isAfter(now.plus(Duration.ofMinutes(5))) || signedAt.isBefore(now.minus(maxAge)))
            throw new PacketException("PACKET_EXPIRED", "Packet is outside the accepted offline synchronization window", 422);
    }

    private byte[] aad(PaymentPacketRequest p) {
        return String.join("\n", "meshpay-payment-aad-v1", p.packetId(), p.senderVpa().toLowerCase(), p.senderNonce())
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] canonicalEnvelope(PaymentPacketRequest p, byte[] iv, byte[] key, byte[] ciphertext) {
        return String.join("\n", "meshpay-encrypted-packet-v1", p.packetId(), p.senderVpa().toLowerCase(), p.senderNonce(),
                Base64.getEncoder().encodeToString(iv), Base64.getEncoder().encodeToString(key),
                Base64.getEncoder().encodeToString(ciphertext)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decode(String value, String label) {
        try { return Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new PacketException("MALFORMED_" + label.toUpperCase().replace(' ', '_'), "Packet encoding is invalid", 400, e); }
    }

    public record VerifiedPacket(DecryptedPaymentPacket payment, String packetHash) {}
    public static class PacketException extends RuntimeException {
        private final String code; private final int status;
        public PacketException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
        public PacketException(String code, String message, int status, Throwable cause) { super(message, cause); this.code = code; this.status = status; }
        public String getCode() { return code; } public int getStatus() { return status; }
    }
}
