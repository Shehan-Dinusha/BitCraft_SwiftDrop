package com.swiftdrop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Global CORS configuration for the reactive gateway.
 * Allowed origins are supplied through {@code CORS_ALLOWED_ORIGINS} as a
 * comma-separated list so each environment can restrict browser access.
 */
@Configuration
public class CorsGlobalConfiguration {

    private final List<String> allowedOrigins;

    public CorsGlobalConfiguration(
            @Value("${swiftdrop.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration());
        return new CorsWebFilter(source);
    }

    CorsConfiguration corsConfiguration() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(allowedOrigins);
        corsConfig.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        corsConfig.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Request-ID",
                "X-Correlation-Id",
                "X-API-Key",
                "X-Merchant-Api-Key",
                "Accept",
                "Origin"
        ));
        corsConfig.setExposedHeaders(List.of(
                "X-Correlation-Id",
                "X-Request-ID",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "Retry-After"
        ));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);
        return corsConfig;
    }
}
