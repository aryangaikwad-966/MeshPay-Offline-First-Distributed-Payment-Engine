package com.demo.meshpay.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    @Profile("dev")
    public SecurityFilterChain developmentSecurity(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    @Profile("!dev")
    public SecurityFilterChain securedSecurity(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/api/payment/health").permitAll()
                        .requestMatchers("/actuator/prometheus").hasAuthority("SCOPE_meshpay:metrics:read")
                        .requestMatchers("/api/payment/ops/**").hasAuthority("SCOPE_meshpay:ops")
                        .requestMatchers("/actuator/**").hasAuthority("SCOPE_meshpay:ops")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {})).build();
    }

    @Bean
    @Profile("!dev")
    JwtDecoder jwtDecoder(@Value("${MESHPAY_JWKS_URI:}") String jwksUri,
                          @Value("${MESHPAY_JWT_ISSUER:}") String issuer,
                          @Value("${MESHPAY_JWT_AUDIENCE:}") String audience) {
        if (jwksUri.isBlank() || issuer.isBlank() || audience.isBlank())
            throw new IllegalStateException("MESHPAY_JWKS_URI, MESHPAY_JWT_ISSUER, and MESHPAY_JWT_AUDIENCE are required outside the dev profile");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
