package com.demo.syncpay.payment.security;

import java.util.List;

/** Supplies dynamically registered device signing keys for a sender VPA. */
public interface DeviceKeyLookup {
    List<String> activePublicKeys(String vpa);
}
