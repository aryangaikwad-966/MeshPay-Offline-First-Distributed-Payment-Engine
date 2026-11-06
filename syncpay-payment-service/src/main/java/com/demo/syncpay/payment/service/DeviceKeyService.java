package com.demo.syncpay.payment.service;

import com.demo.syncpay.payment.model.DeviceKey;
import com.demo.syncpay.payment.repository.DeviceKeyRepository;
import com.demo.syncpay.payment.security.DeviceKeyLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Registers, lists, and revokes device-generated signing keys. Registration
 * validates that the submitted key is a base64 X.509 RSA public key of at
 * least 2048 bits before it can be used to verify offline packets.
 */
@Service
@RequiredArgsConstructor
public class DeviceKeyService implements DeviceKeyLookup {

    private final DeviceKeyRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<String> activePublicKeys(String vpa) {
        return repository.findByVpaAndStatus(vpa.toLowerCase(), DeviceKey.Status.ACTIVE)
                .stream().map(DeviceKey::getPublicKey).toList();
    }

    @Transactional
    public DeviceKey register(String vpa, String deviceId, String publicKeyBase64) {
        validatePublicKey(publicKeyBase64);
        String normalizedVpa = vpa.toLowerCase();
        return repository.findByVpaAndDeviceId(normalizedVpa, deviceId)
                .map(existing -> {
                    existing.rotate(publicKeyBase64);
                    return repository.save(existing);
                })
                .orElseGet(() -> repository.save(DeviceKey.register(normalizedVpa, deviceId, publicKeyBase64)));
    }

    @Transactional(readOnly = true)
    public List<DeviceKey> list(String vpa) {
        return repository.findByVpaOrderByRegisteredAtDesc(vpa.toLowerCase());
    }

    @Transactional
    public int revoke(String vpa, String deviceId) {
        List<DeviceKey> keys = repository.findByVpaOrderByRegisteredAtDesc(vpa.toLowerCase()).stream()
                .filter(k -> deviceId == null || deviceId.isBlank() || k.getDeviceId().equals(deviceId))
                .toList();
        int count = 0;
        for (DeviceKey key : keys) {
            if (key.getStatus() == DeviceKey.Status.ACTIVE) {
                key.revoke();
                count++;
            }
        }
        return count;
    }

    private static void validatePublicKey(String publicKeyBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048)
                throw new IllegalArgumentException("Signing key must be an RSA public key of at least 2048 bits");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Signing key must be a base64-encoded X.509 RSA public key", e);
        }
    }
}
