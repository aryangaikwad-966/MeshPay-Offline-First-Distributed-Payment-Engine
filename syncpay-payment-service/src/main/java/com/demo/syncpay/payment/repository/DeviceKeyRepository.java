package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.DeviceKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceKeyRepository extends JpaRepository<DeviceKey, UUID> {
    Optional<DeviceKey> findByVpaAndDeviceId(String vpa, String deviceId);
    List<DeviceKey> findByVpaOrderByRegisteredAtDesc(String vpa);
    List<DeviceKey> findByVpaAndStatus(String vpa, DeviceKey.Status status);
}
