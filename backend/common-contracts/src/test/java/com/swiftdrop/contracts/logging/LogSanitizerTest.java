package com.swiftdrop.contracts.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    @DisplayName("Escapes CR and LF so a value cannot start a forged log line")
    void escapesLineBreaks() {
        String forged = "/api/v1/orders\r\n2026-11-19 INFO  Admin login succeeded";

        String cleaned = LogSanitizer.clean(forged);

        assertThat(cleaned).doesNotContain("\r", "\n");
        assertThat(cleaned).isEqualTo("/api/v1/orders\\r\\n2026-11-19 INFO  Admin login succeeded");
    }

    @Test
    @DisplayName("Leaves ordinary values unchanged")
    void keepsOrdinaryValues() {
        assertThat(LogSanitizer.clean("/api/v1/deliveries/42/transition")).isEqualTo("/api/v1/deliveries/42/transition");
        assertThat(LogSanitizer.clean("")).isEmpty();
    }

    @Test
    @DisplayName("Handles null and non-string values via String.valueOf")
    void handlesNullAndObjects() {
        assertThat(LogSanitizer.clean(null)).isEqualTo("null");
        assertThat(LogSanitizer.clean(Map.of("field", "bad\nvalue"))).isEqualTo("{field=bad\\nvalue}");
        assertThat(LogSanitizer.clean(List.of(1, 2))).isEqualTo("[1, 2]");
    }
}
