package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.domain.repository.RecipientEmailStopRepository;
import com.swiftdrop.notification.service.RecipientEmailStopService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The recipient's "Stop these emails" link, against real PostgreSQL (migration V16). */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class RecipientEmailStopIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecipientEmailStopService stops;

    @Autowired
    private RecipientEmailStopRepository repository;

    private void stop(String token, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/notifications/email-stops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("One token per parcel; the link stops only that parcel, and using it again is fine")
    void stopsOneParcel() throws Exception {
        UUID parcel = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String token = stops.tokenFor(parcel);

        assertThat(token).matches(RecipientEmailStopService.TOKEN);
        assertThat(stops.tokenFor(parcel)).isEqualTo(token);
        assertThat(stops.tokenFor(other)).isNotEqualTo(token);
        assertThat(stops.isStopped(parcel)).isFalse();

        stop(token, 200);
        stop(token, 200);

        assertThat(stops.isStopped(parcel)).isTrue();
        assertThat(stops.isStopped(other)).isFalse();
        assertThat(repository.findById(parcel)).get().satisfies(row -> assertThat(row.getStoppedAt()).isNotNull());
    }

    @Test
    @DisplayName("An unknown or malformed token is a 404 or 400 and stops nothing")
    void badTokens() throws Exception {
        stop("q3kN0e6Zb2v8Yx1RjT5wL9mPa4sDc7Hf0uGi3KoBnEz", 404);
        stop("not-a-token", 404);
        mvc.perform(post("/api/v1/notifications/email-stops").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
