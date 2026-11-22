package com.swiftdrop.iam;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real PostgreSQL, Redis and RabbitMQ for the IAM integration tests, started once per test JVM and shared
 * by every subclass (Ryuk removes them when the JVM exits). Flyway runs every migration from scratch, so
 * the schema is exactly what production gets. Images match docker-compose.yml. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class IamContainers {

    /** RabbitMQ's management image can take over a minute to boot on a busy Docker host. */
    private static final Duration STARTUP = Duration.ofMinutes(3);

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withStartupTimeout(STARTUP);

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withStartupTimeout(STARTUP);

    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine").withStartupTimeout(STARTUP);

    static {
        // Started here rather than with @Container so the three test classes reuse one set of containers.
        // Without Docker, @Testcontainers(disabledWithoutDocker) skips the class before this matters.
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
            RABBIT.start();
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
}
