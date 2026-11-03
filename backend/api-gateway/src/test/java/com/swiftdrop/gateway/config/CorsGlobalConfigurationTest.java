package com.swiftdrop.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsGlobalConfigurationTest {

    @Test
    void allowsConfiguredOrigin() {
        CorsConfiguration config = new CorsGlobalConfiguration("http://localhost:5173").corsConfiguration();

        assertEquals("http://localhost:5173", config.checkOrigin("http://localhost:5173"));
    }

    @Test
    void rejectsUnknownOrigin() {
        CorsConfiguration config = new CorsGlobalConfiguration("http://localhost:5173").corsConfiguration();

        assertNull(config.checkOrigin("http://evil.test"));
    }

    @Test
    void parsesCommaSeparatedOrigins() {
        CorsConfiguration config = new CorsGlobalConfiguration(
                "http://localhost:5173, https://app.example.test").corsConfiguration();

        assertEquals("http://localhost:5173", config.checkOrigin("http://localhost:5173"));
        assertEquals("https://app.example.test", config.checkOrigin("https://app.example.test"));
    }
}
