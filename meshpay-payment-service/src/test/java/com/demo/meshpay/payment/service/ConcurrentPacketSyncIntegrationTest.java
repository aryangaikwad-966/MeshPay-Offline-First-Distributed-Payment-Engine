package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.api.DecryptedPaymentPacket;
import com.demo.meshpay.payment.api.PacketSyncResponse;
import com.demo.meshpay.payment.api.PaymentPacketRequest;
import com.demo.meshpay.payment.model.Outbox;
import com.demo.meshpay.payment.repository.OutboxRepository;
import com.demo.meshpay.payment.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.context.ActiveProfiles("dev")
class ConcurrentPacketSyncIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static final KeyPair RECIPIENT = rsaKeyPair();
    private static final KeyPair SIGNER = rsaKeyPair();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.data.redis.password", () -> "");
        properties.add("spring.kafka.listener.auto-startup", () -> "false");
        properties.add("eureka.client.enabled", () -> "false");
        properties.add("meshpay.outbox.enabled", () -> "false");
        properties.add("meshpay.packet.server-private-key", () -> b64(RECIPIENT.getPrivate().getEncoded()));
        properties.add("meshpay.packet.server-public-key", () -> b64(RECIPIENT.getPublic().getEncoded()));
        properties.add("meshpay.packet.trusted-signers", () -> "alice@demo=" + b64(SIGNER.getPublic().getEncoded()));
    }

    @Autowired PaymentPacketSyncService syncService;
    @Autowired TransactionRepository transactions;
    @Autowired OutboxRepository outbox;
    @Autowired ObjectMapper mapper;

    @Test
    void concurrentCopiesCreateOneDurablePaymentAndOneOutboxEffect() throws Exception {
        PaymentPacketRequest packet = encryptedPacket("concurrent-packet", "shared-random-nonce");
        int callers = 24;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PacketSyncResponse>> responses = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                responses.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return syncService.synchronize(packet);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            long accepted = 0;
            for (Future<PacketSyncResponse> future : responses) {
                String status = future.get(30, TimeUnit.SECONDS).status();
                if (status.equals("SYNCHRONIZED")) accepted++;
                assertTrue(Set.of("SYNCHRONIZED", "DUPLICATE", "IN_PROGRESS").contains(status));
            }
            assertEquals(1, accepted, "one caller must report the initial durable commit");
            assertEquals(1, transactions.findAll().stream().filter(t -> t.getPacketId().equals("concurrent-packet")).count());
            assertEquals(1, outbox.findAll().stream().filter(e -> "PAYMENT_PACKET_SYNCHRONIZED".equals(e.getEventType())).count());
            assertEquals("DUPLICATE", syncService.synchronize(packet).status());
        } finally {
            pool.shutdownNow();
        }
    }

    private PaymentPacketRequest encryptedPacket(String packetId, String nonce) throws Exception {
        DecryptedPaymentPacket payment = new DecryptedPaymentPacket(packetId, "alice@demo", "bob@demo",
                new BigDecimal("5.25"), "USD", Instant.now().toEpochMilli(), nonce);
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        byte[] aes = new byte[32]; new SecureRandom().nextBytes(aes);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(aes, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(String.join("\n", "meshpay-payment-aad-v1", packetId, "alice@demo", nonce).getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(mapper.writeValueAsBytes(payment));
        Cipher wrapper = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        wrapper.init(Cipher.ENCRYPT_MODE, RECIPIENT.getPublic(), new OAEPParameterSpec("SHA-256", "MGF1",
                MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] wrapped = wrapper.doFinal(aes); Arrays.fill(aes, (byte) 0);
        String iv64 = b64(iv), wrapped64 = b64(wrapped), ciphertext64 = b64(ciphertext);
        String canonical = String.join("\n", "meshpay-encrypted-packet-v1", packetId, "alice@demo", nonce, iv64, wrapped64, ciphertext64);
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        signature.initSign(SIGNER.getPrivate()); signature.update(canonical.getBytes(StandardCharsets.UTF_8));
        return new PaymentPacketRequest(packetId, "alice@demo", nonce, iv64, wrapped64, ciphertext64,
                b64(signature.sign()), "bridge-1", 0);
    }

    private static KeyPair rsaKeyPair() {
        try { KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (GeneralSecurityException e) { throw new ExceptionInInitializerError(e); }
    }
    private static String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
