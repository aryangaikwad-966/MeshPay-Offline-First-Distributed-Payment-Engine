package com.demo.syncpay.payment.controller;

import com.demo.syncpay.payment.api.DeviceKeyView;
import com.demo.syncpay.payment.api.DeviceRegistrationRequest;
import com.demo.syncpay.payment.api.DeviceRegistrationResponse;
import com.demo.syncpay.payment.api.DeviceStatusResponse;
import com.demo.syncpay.payment.model.DeviceKey;
import com.demo.syncpay.payment.service.DeviceKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment/devices")
@RequiredArgsConstructor
@Tag(name = "Device signing keys")
public class DeviceController {

    private final DeviceKeyService deviceKeys;

    @PostMapping("/register")
    @Operation(summary = "Register or rotate a device-generated RSA signing public key")
    public ResponseEntity<DeviceRegistrationResponse> register(@Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceKey key = deviceKeys.register(request.vpa(), request.deviceId(), request.publicKey());
        return ResponseEntity.ok(new DeviceRegistrationResponse(key.getVpa(), key.getDeviceId(),
                key.getStatus().name(), key.getRegisteredAt()));
    }

    @GetMapping("/{vpa}")
    @Operation(summary = "List registered signing devices for a sender VPA")
    public DeviceStatusResponse status(@PathVariable @Pattern(regexp = "[A-Za-z0-9._-]{1,64}@[A-Za-z0-9.-]{1,64}") String vpa) {
        List<DeviceKeyView> devices = deviceKeys.list(vpa).stream()
                .map(k -> new DeviceKeyView(k.getDeviceId(), k.getStatus().name(), k.getRegisteredAt(), k.getRevokedAt()))
                .toList();
        return new DeviceStatusResponse(vpa.toLowerCase(), devices);
    }

    @PostMapping("/{vpa}/revoke")
    @Operation(summary = "Revoke device signing keys; omitting deviceId revokes all devices for the VPA")
    public Map<String, Object> revoke(@PathVariable @Pattern(regexp = "[A-Za-z0-9._-]{1,64}@[A-Za-z0-9.-]{1,64}") String vpa,
                                      @RequestBody(required = false) Map<String, String> body) {
        String deviceId = body == null ? null : body.get("deviceId");
        int revoked = deviceKeys.revoke(vpa, deviceId);
        return Map.of("vpa", vpa.toLowerCase(), "revoked", revoked);
    }
}
