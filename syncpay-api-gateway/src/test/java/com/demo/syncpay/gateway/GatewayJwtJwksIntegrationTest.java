package com.demo.meshpay.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@WebFluxTest(controllers = GatewayJwtJwksIntegrationTest.ProbeController.class)
@Import({GatewaySecurityConfig.class, GatewayJwtJwksIntegrationTest.ProbeController.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "MESHPAY_JWT_ISSUER=https://meshpay-gateway.test/",
        "MESHPAY_JWT_AUDIENCE=meshpay"
})
class GatewayJwtJwksIntegrationTest {
    private static final String ISSUER = "https://meshpay-gateway.test/";
    private static final String AUDIENCE = "meshpay";
    private static RSAKey trustedKey;
    private static RSAKey untrustedKey;
    private static HttpServer jwksServer;

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void jwksProperties(DynamicPropertyRegistry registry) {
        ensureTestIssuer();
        registry.add("MESHPAY_JWKS_URI", () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
    }

    @BeforeAll
    static void startTestIssuer() { ensureTestIssuer(); }

    @AfterAll
    static void stopTestIssuer() { if (jwksServer != null) jwksServer.stop(0); }

    @Test
    void validSignedJwtAndGatewayScopeAreAccepted() {
        client.get().uri("/api/payment/ops/probe").header("Authorization", bearer(token(trustedKey, ISSUER,
                        AUDIENCE, Instant.now().plusSeconds(120), "meshpay:ops")))
                .exchange().expectStatus().isOk();
    }

    @Test
    void missingScopeIsForbiddenAndAnonymousRequestIsUnauthorized() {
        client.get().uri("/api/payment/ops/probe").header("Authorization", bearer(token(trustedKey, ISSUER,
                        AUDIENCE, Instant.now().plusSeconds(120), "meshpay:metrics:read")))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/payment/ops/probe").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void expiredWrongIssuerWrongAudienceMalformedAndWronglySignedTokensAreRejected() {
        assertUnauthorized(token(trustedKey, ISSUER, AUDIENCE, Instant.now().minusSeconds(30), "meshpay:ops"));
        assertUnauthorized(token(trustedKey, "https://wrong-issuer.test/", AUDIENCE,
                Instant.now().plusSeconds(120), "meshpay:ops"));
        assertUnauthorized(token(trustedKey, ISSUER, "wrong-audience", Instant.now().plusSeconds(120), "meshpay:ops"));
        assertUnauthorized(token(untrustedKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(120), "meshpay:ops"));
        client.get().uri("/api/payment/ops/probe").header("Authorization", "Bearer malformed.token.value")
                .exchange().expectStatus().isUnauthorized();
    }

    private void assertUnauthorized(String token) {
        client.get().uri("/api/payment/ops/probe").header("Authorization", bearer(token))
                .exchange().expectStatus().isUnauthorized();
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private static String token(RSAKey key, String issuer, String audience, Instant expiresAt, String scope) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer).subject("meshpay-test-operator").audience(audience)
                    .issueTime(Date.from(now.minusSeconds(1))).notBeforeTime(Date.from(now.minusSeconds(1)))
                    .expirationTime(Date.from(expiresAt)).claim("scope", scope).build();
            SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            signed.sign(new RSASSASigner(key));
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign local gateway test JWT", e);
        }
    }

    private static synchronized void ensureTestIssuer() {
        if (jwksServer != null) return;
        try {
            trustedKey = new RSAKeyGenerator(2048).keyID("meshpay-gateway-integration-key").generate();
            untrustedKey = new RSAKeyGenerator(2048).keyID("untrusted-gateway-integration-key").generate();
            byte[] body = new JWKSet(trustedKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksServer.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            jwksServer.start();
        } catch (Exception e) {
            throw new IllegalStateException("Could not start local gateway test JWKS issuer", e);
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/payment/ops/probe")
        String operationsProbe() { return "ok"; }
    }
}
