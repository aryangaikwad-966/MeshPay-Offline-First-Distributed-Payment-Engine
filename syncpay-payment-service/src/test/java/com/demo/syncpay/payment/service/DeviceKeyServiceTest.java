package com.demo.syncpay.payment.service;

import com.demo.syncpay.payment.model.DeviceKey;
import com.demo.syncpay.payment.repository.DeviceKeyRepository;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceKeyServiceTest {

    private final DeviceKeyRepository repository = mock(DeviceKeyRepository.class);
    private final DeviceKeyService service = new DeviceKeyService(repository);

    private static String publicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    }

    @Test
    void registersADeviceKey() throws Exception {
        when(repository.findByVpaAndDeviceId("alice@demo", "device-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DeviceKey saved = service.register("Alice@Demo", "device-1", publicKey());
        assertEquals("alice@demo", saved.getVpa());
        assertEquals("device-1", saved.getDeviceId());
        assertEquals(DeviceKey.Status.ACTIVE, saved.getStatus());
    }

    @Test
    void reRegistrationRotatesKeyMaterial() throws Exception {
        DeviceKey existing = DeviceKey.register("alice@demo", "device-1", publicKey());
        existing.revoke();
        when(repository.findByVpaAndDeviceId("alice@demo", "device-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DeviceKey rotated = service.register("alice@demo", "device-1", publicKey());
        assertEquals(DeviceKey.Status.ACTIVE, rotated.getStatus());
        assertNull(rotated.getRevokedAt());
    }

    @Test
    void rejectsWeakOrMalformedKeys() {
        assertThrows(IllegalArgumentException.class, () -> service.register("alice@demo", "d", "not-base64!!"));
        assertThrows(IllegalArgumentException.class, () -> service.register("alice@demo", "d", Base64.getEncoder().encodeToString(new byte[64])));
        verify(repository, never()).save(any());
    }

    @Test
    void activePublicKeysOnlyReturnsActiveRegistrations() {
        DeviceKey active = DeviceKey.register("alice@demo", "d1", "a2V5MQ==");
        DeviceKey revoked = DeviceKey.register("alice@demo", "d2", "a2V5Mg==");
        revoked.revoke();
        when(repository.findByVpaAndStatus("alice@demo", DeviceKey.Status.ACTIVE)).thenReturn(List.of(active));
        assertEquals(List.of("a2V5MQ=="), service.activePublicKeys("ALICE@DEMO"));
    }

    @Test
    void revokeAllForVpa() {
        DeviceKey a = DeviceKey.register("alice@demo", "d1", "a2V5MQ==");
        DeviceKey b = DeviceKey.register("alice@demo", "d2", "a2V5Mg==");
        b.revoke();
        when(repository.findByVpaOrderByRegisteredAtDesc("alice@demo")).thenReturn(List.of(a, b));
        assertEquals(1, service.revoke("alice@demo", null));
        assertEquals(DeviceKey.Status.REVOKED, a.getStatus());
    }

    @Test
    void revokeSingleDevice() {
        DeviceKey a = DeviceKey.register("alice@demo", "d1", "a2V5MQ==");
        DeviceKey b = DeviceKey.register("alice@demo", "d2", "a2V5Mg==");
        when(repository.findByVpaOrderByRegisteredAtDesc("alice@demo")).thenReturn(List.of(a, b));
        assertEquals(1, service.revoke("alice@demo", "d2"));
        assertEquals(DeviceKey.Status.ACTIVE, a.getStatus());
        assertEquals(DeviceKey.Status.REVOKED, b.getStatus());
    }
}
