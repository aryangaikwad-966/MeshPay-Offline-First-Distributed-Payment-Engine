package com.demo.meshpay.payment.security;

import com.demo.meshpay.payment.api.DecryptedPaymentPacket;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.math.BigDecimal;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PacketCryptographyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static KeyPair recipient;
    private static KeyPair signer;
    private static PacketCryptography cryptography;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        recipient = generator.generateKeyPair();
        signer = generator.generateKeyPair();
        cryptography = new PacketCryptography(MAPPER, b64(recipient.getPrivate().getEncoded()),
                b64(recipient.getPublic().getEncoded()), "alice@demo=" + b64(signer.getPublic().getEncoded()), Duration.ofDays(30));
    }

    @Test
    void encryptsAndAuthenticatesAnOfflinePacket() throws Exception {
        PaymentPacketRequest packet = createPacket("packet-a", "nonce-a");
        PacketCryptography.VerifiedPacket verified = cryptography.verifyAndDecrypt(packet);
        assertEquals("alice@demo", verified.payment().senderVpa());
        assertEquals("bob@demo", verified.payment().receiverVpa());
        assertEquals(new BigDecimal("12.50"), verified.payment().amount());
        assertEquals(64, verified.packetHash().length());
    }

    @Test
    void rejectsAnInvalidSenderSignature() throws Exception {
        PaymentPacketRequest packet = createPacket("packet-b", "nonce-b");
        String invalidSignature = b64(new byte[256]);
        PaymentPacketRequest tampered = new PaymentPacketRequest(packet.packetId(), packet.senderVpa(), packet.senderNonce(),
                packet.iv(), packet.wrappedAesKey(), packet.ciphertext(), invalidSignature, packet.bridgeNodeId(), packet.hopCount());
        PacketCryptography.PacketException error = assertThrows(PacketCryptography.PacketException.class,
                () -> cryptography.verifyAndDecrypt(tampered));
        assertEquals("INVALID_SIGNATURE", error.getCode());
    }

    @Test
    void rejectsTamperedCiphertextEvenWhenItHasAValidSenderSignature() throws Exception {
        PaymentPacketRequest packet = createPacket("packet-c", "nonce-c");
        byte[] ciphertext = Base64.getDecoder().decode(packet.ciphertext());
        ciphertext[ciphertext.length - 1] ^= 1;
        PaymentPacketRequest tampered = sign(packet.packetId(), packet.senderVpa(), packet.senderNonce(), packet.iv(),
                packet.wrappedAesKey(), b64(ciphertext));
        PacketCryptography.PacketException error = assertThrows(PacketCryptography.PacketException.class,
                () -> cryptography.verifyAndDecrypt(tampered));
        assertEquals("PACKET_AUTHENTICATION_FAILED", error.getCode());
    }

    @Test
    void rejectsExpiredPackets() throws Exception {
        DecryptedPaymentPacket payment = new DecryptedPaymentPacket("packet-expired", "alice@demo", "bob@demo",
                new BigDecimal("12.50"), "USD", Instant.now().minus(Duration.ofDays(31)).toEpochMilli(), "nonce-expired");
        PaymentPacketRequest packet = encryptAndSign(payment);
        PacketCryptography.PacketException error = assertThrows(PacketCryptography.PacketException.class,
                () -> cryptography.verifyAndDecrypt(packet));
        assertEquals("PACKET_EXPIRED", error.getCode());
    }

    private static PaymentPacketRequest createPacket(String id, String nonce) throws Exception {
        return encryptAndSign(new DecryptedPaymentPacket(id, "alice@demo", "bob@demo",
                new BigDecimal("12.50"), "USD", Instant.now().toEpochMilli(), nonce));
    }

    private static PaymentPacketRequest encryptAndSign(DecryptedPaymentPacket payment) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        String ivText = b64(iv);
        byte[] aes = new byte[32];
        SecureRandom.getInstanceStrong().nextBytes(aes);
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesCipher.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(aes, "AES"), new GCMParameterSpec(128, iv));
        aesCipher.updateAAD(aad(payment));
        byte[] ciphertext = aesCipher.doFinal(MAPPER.writeValueAsBytes(payment));
        Cipher keyCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        keyCipher.init(Cipher.ENCRYPT_MODE, recipient.getPublic(), new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        String wrappedKey = b64(keyCipher.doFinal(aes));
        java.util.Arrays.fill(aes, (byte) 0);
        return sign(payment.packetId(), payment.senderVpa(), payment.senderNonce(), ivText, wrappedKey, b64(ciphertext));
    }

    private static byte[] aad(DecryptedPaymentPacket p) {
        return String.join("\n", "meshpay-payment-aad-v1", p.packetId(), p.senderVpa().toLowerCase(), p.senderNonce()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static PaymentPacketRequest sign(String id, String sender, String nonce, String iv, String key, String ciphertext) throws Exception {
        String canonical = String.join("\n", "meshpay-encrypted-packet-v1", id, sender.toLowerCase(), nonce, iv, key, ciphertext);
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        signature.initSign(signer.getPrivate());
        signature.update(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new PaymentPacketRequest(id, sender, nonce, iv, key, ciphertext, b64(signature.sign()), "bridge-1", 0);
    }

    private static String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
