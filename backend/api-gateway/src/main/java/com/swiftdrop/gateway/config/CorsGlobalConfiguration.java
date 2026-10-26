package com.swiftdrop.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Global CORS configuration for the reactive gateway.
 * In production, allowed origins are restricted to the SwiftDrop dashboard domain.
 */
@Configuration
public class CorsGlobalConfiguration {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of(
                "https://app.swiftdrop.io",
                "https://*.swiftdrop.io",
                "http://localhost:[*]"
        ));
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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
