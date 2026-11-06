package com.demo.syncpay.payment.api;

import java.util.List;

public record DeviceStatusResponse(String vpa, List<DeviceKeyView> devices) {}
