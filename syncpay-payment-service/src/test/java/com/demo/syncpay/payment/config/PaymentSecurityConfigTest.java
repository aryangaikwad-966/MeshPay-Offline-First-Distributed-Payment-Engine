package com.demo.meshpay.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentSecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, PaymentSecurityConfigTest.ProbeController.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "MESHPAY_JWKS_URI=https://issuer.example.test/keys",
        "MESHPAY_JWT_ISSUER=https://issuer.example.test/",
        "MESHPAY_JWT_AUDIENCE=meshpay"
})
class PaymentSecurityConfigTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void paymentOperationsRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/api/payment/ops/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void paymentOperationsRequireOperationsScope() throws Exception {
        mvc.perform(get("/api/payment/ops/probe")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_meshpay:metrics:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentOperationsAcceptOperationsScope() throws Exception {
        mvc.perform(get("/api/payment/ops/probe")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_meshpay:ops"))))
                .andExpect(status().isOk());
    }

    @Test
    void healthIsPublicButPrometheusRequiresMetricsScope() throws Exception {
        mvc.perform(get("/api/payment/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_meshpay:metrics:read"))))
                .andExpect(status().isOk());
    }

    @RestController
    public static class ProbeController {
        @GetMapping("/api/payment/ops/probe")
        public String operations() { return "ok"; }

        @GetMapping("/api/payment/health")
        public String health() { return "ok"; }

        @GetMapping("/actuator/prometheus")
        public String metrics() { return "ok"; }
    }
}
