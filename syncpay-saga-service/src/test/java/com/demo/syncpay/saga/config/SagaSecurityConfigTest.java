package com.demo.syncpay.saga.config;

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

@WebMvcTest(controllers = SagaSecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, SagaSecurityConfigTest.ProbeController.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "SYNCPAY_JWKS_URI=https://issuer.example.test/keys",
        "SYNCPAY_JWT_ISSUER=https://issuer.example.test/",
        "SYNCPAY_JWT_AUDIENCE=syncpay"
})
class SagaSecurityConfigTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void sagaOperationsRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/api/saga/ops/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void sagaOperationsRequireOperationsScope() throws Exception {
        mvc.perform(get("/api/saga/ops/probe")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_syncpay:metrics:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sagaOperationsAcceptOperationsScope() throws Exception {
        mvc.perform(get("/api/saga/ops/probe")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_syncpay:ops"))))
                .andExpect(status().isOk());
    }

    @RestController
    public static class ProbeController {
        @GetMapping("/api/saga/ops/probe")
        public String operations() { return "ok"; }
    }
}
