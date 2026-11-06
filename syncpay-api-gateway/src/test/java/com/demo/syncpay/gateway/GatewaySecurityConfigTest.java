package com.demo.syncpay.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = FallbackController.class)
@Import({GatewaySecurityConfig.class, GatewayCorsConfig.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "SYNCPAY_JWKS_URI=https://issuer.example.test/keys",
        "SYNCPAY_JWT_ISSUER=https://issuer.example.test/",
        "SYNCPAY_JWT_AUDIENCE=syncpay"
})
class GatewaySecurityConfigTest {
    @Autowired
    private WebTestClient client;

    @Test
    void operationsRoutesRejectAnonymousRequests() {
        client.get().uri("/api/payment/ops/overview")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void operationsRoutesRejectAuthenticatedUsersWithoutOperationsScope() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_syncpay:metrics:read")))
                .get().uri("/api/saga/ops/sagas")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void gatewayDiagnosticsRejectAnonymousRequests() {
        client.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicHealthAndFallbackRemainReachable() {
        client.get().uri("/fallback/payment")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("PAYMENT_SERVICE_UNAVAILABLE");
    }
}
