package com.demo.meshpay.payment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PacketMetrics {
    private final MeterRegistry registry;
    public PacketMetrics(MeterRegistry registry) { this.registry = registry; }
    public void accepted() { registry.counter("meshpay.packet.accepted").increment(); }
    public void rejected() { registry.counter("meshpay.packet.rejected").increment(); }
    public void duplicate() { registry.counter("meshpay.packet.duplicate").increment(); }
    public void replay() { registry.counter("meshpay.packet.replay").increment(); }
    public void redisFallback() { registry.counter("meshpay.idempotency.redis.fallback").increment(); }
}
