package com.demo.meshpay.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for Payment Service
 * Allows cross-origin requests from specified origins
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow credentials
        config.setAllowCredentials(true);
        
        // Allowed origins (configure via environment variable in production)
        String allowedOriginsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        String allowedOrigins = allowedOriginsEnv != null ? allowedOriginsEnv : "http://localhost:3000,http://localhost:8080";
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        
        // Allowed methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Allowed headers
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // Exposed headers
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        
        // Max age for preflight requests
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
