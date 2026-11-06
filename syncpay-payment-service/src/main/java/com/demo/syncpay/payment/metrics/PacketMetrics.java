package com.demo.syncpay.payment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PacketMetrics {
    private final MeterRegistry registry;
    public PacketMetrics(MeterRegistry registry) { this.registry = registry; }
    public void accepted() { registry.counter("syncpay.packet.accepted").increment(); }
    public void rejected() { registry.counter("syncpay.packet.rejected").increment(); }
    public void duplicate() { registry.counter("syncpay.packet.duplicate").increment(); }
    public void replay() { registry.counter("syncpay.packet.replay").increment(); }
    public void redisFallback() { registry.counter("syncpay.idempotency.redis.fallback").increment(); }
}
