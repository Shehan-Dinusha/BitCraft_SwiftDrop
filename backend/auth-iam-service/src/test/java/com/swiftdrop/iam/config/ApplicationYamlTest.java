package com.swiftdrop.iam.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses the service's YAML with the same loader Spring Boot uses at startup. A duplicate key (for
 * example two {@code swiftdrop.auth} blocks) makes the service crash before it starts, and no other
 * unit test loads these files.
 */
class ApplicationYamlTest {

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-aks.yml"})
    void yamlLoadsWithoutErrors(String file) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        assertThat(sources).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "swiftdrop.auth.refresh-cookie.secure",
            "swiftdrop.auth.mfa.enabled",
            "swiftdrop.auth.otp.ttl-minutes",
            "swiftdrop.jwt.issuer"})
    void authSettingsAreAllPresent(String key) throws Exception {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources.getFirst().containsProperty(key)).as(key).isTrue();
    }
}
