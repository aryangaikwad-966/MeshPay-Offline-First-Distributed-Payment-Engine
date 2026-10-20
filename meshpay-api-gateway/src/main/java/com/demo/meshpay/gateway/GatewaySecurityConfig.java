package com.demo.meshpay.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private static final String OPS = "SCOPE_meshpay:ops";
    private static final String METRICS = "SCOPE_meshpay:metrics:read";

    @Bean
    @Profile("dev")
    SecurityWebFilterChain developmentSecurity(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Profile("!dev")
    SecurityWebFilterChain securedSecurity(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/fallback/**").permitAll()
                        .pathMatchers("/actuator/prometheus").hasAuthority(METRICS)
                        .pathMatchers("/actuator/gateway/**", "/actuator/**").hasAuthority(OPS)
                        .pathMatchers("/api/payment/ops/**", "/api/saga/ops/**").hasAuthority(OPS)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @Profile("!dev")
    ReactiveJwtDecoder gatewayJwtDecoder(
            @Value("${MESHPAY_JWKS_URI:}") String jwksUri,
            @Value("${MESHPAY_JWT_ISSUER:}") String issuer,
            @Value("${MESHPAY_JWT_AUDIENCE:}") String audience) {
        if (jwksUri.isBlank() || issuer.isBlank() || audience.isBlank()) {
            throw new IllegalStateException(
                    "MESHPAY_JWKS_URI, MESHPAY_JWT_ISSUER, and MESHPAY_JWT_AUDIENCE are required outside the dev profile");
        }

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
