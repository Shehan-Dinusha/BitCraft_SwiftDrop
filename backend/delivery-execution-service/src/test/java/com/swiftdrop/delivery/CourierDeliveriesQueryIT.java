package com.swiftdrop.delivery;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourierDeliveriesQueryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DeliverySessionRepository repository;

    private final UUID courierId = UUID.randomUUID();
    private final Instant startOfDay = Instant.parse("2026-11-22T18:30:00Z");
    private final Instant endOfDay = startOfDay.plus(Duration.ofDays(1));

    @Test
    @DisplayName("Today's list keeps a job still open from an earlier day, and drops one that ended then")
    void keepsOpenJobsFromEarlierDays() {
        Instant daysAgo = startOfDay.minus(Duration.ofDays(3));
        Instant today = startOfDay.plus(Duration.ofHours(2));
        save("SD-OPEN-OLD1", courierId, DeliveryState.ASSIGNED, daysAgo);
        save("SD-DONE-OLD1", courierId, DeliveryState.DELIVERED, daysAgo);
        save("SD-FAIL-OLD1", courierId, DeliveryState.FAILED, daysAgo);
        save("SD-DONE-NEW1", courierId, DeliveryState.DELIVERED, today);
        save("SD-OTHR-NEW1", UUID.randomUUID(), DeliveryState.ASSIGNED, today);

        assertThat(repository.findTodayAndOpenByCourierId(courierId, startOfDay, endOfDay))
                .extracting(DeliverySession::getTrackingNumber)
                .containsExactly("SD-OPEN-OLD1", "SD-DONE-NEW1");
    }

    private void save(String trackingNumber, UUID courier, DeliveryState state, Instant at) {
        DeliverySession session = new DeliverySession();
        session.setOrderId(UUID.randomUUID());
        session.setTrackingNumber(trackingNumber);
        session.setCourierId(courier);
        session.setCurrentState(state);
        session.setCreatedAt(at);
        session.setUpdatedAt(at);
        repository.saveAndFlush(session);
    }
}
