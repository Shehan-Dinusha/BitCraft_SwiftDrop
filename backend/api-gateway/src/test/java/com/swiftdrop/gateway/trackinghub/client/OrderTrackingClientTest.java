package com.swiftdrop.gateway.trackinghub.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.tracking.HistoryEntry;
import com.swiftdrop.contracts.dto.tracking.InternalTrackingView;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.gateway.config.GatewayRouteProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderTrackingClientTest {

    private MockWebServer mockWebServer;
    private OrderTrackingClient client;
    private GatewayRouteProperties routes;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        routes = mock(GatewayRouteProperties.class);
        when(routes.order()).thenReturn(mockWebServer.url("/").toString());

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();

        objectMapper = new ObjectMapper().findAndRegisterModules();
        client = new OrderTrackingClient(webClient, routes);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void returnsTrackingViewWhenOrderServiceResponds200() {
        InternalTrackingView view = new InternalTrackingView(
                "SD-TEST-001",
                PublicOrderStatus.PICKED_UP,
                List.of(new HistoryEntry(PublicOrderStatus.CREATED, Instant.parse("2026-10-26T06:00:00Z"))),
                "Colombo",
                SriLankanDistrict.COLOMBO,
                "Kandy",
                SriLankanDistrict.KANDY
        );

        ApiResponse<InternalTrackingView> response = ApiResponse.success(view, "Tracking retrieved");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(toJson(response)));

        StepVerifier.create(client.getTracking("SD-TEST-001"))
                .expectNextMatches(opt -> opt.isPresent() &&
                        opt.get().trackingNumber().equals("SD-TEST-001") &&
                        opt.get().status() == PublicOrderStatus.PICKED_UP)
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void returnsEmptyOptionalWhenOrderServiceReturns404() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"success\":false,\"message\":\"Not found\"}"));

        StepVerifier.create(client.getTracking("SD-TEST-404"))
                .expectNextMatches(opt -> opt.isEmpty())
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void returnsEmptyOptionalOn5xxError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"success\":false,\"message\":\"Internal error\"}"));

        StepVerifier.create(client.getTracking("SD-TEST-500"))
                .expectNextMatches(opt -> opt.isEmpty())
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void returnsEmptyOptionalOnTimeout() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                .setBody(toJson(ApiResponse.success(
                        new InternalTrackingView("SD-TIMEOUT", PublicOrderStatus.CREATED, List.of(),
                                "Colombo", SriLankanDistrict.COLOMBO, "Kandy", SriLankanDistrict.KANDY), "ok"))));

        StepVerifier.create(client.getTracking("SD-TIMEOUT"))
                .expectNextMatches(opt -> opt.isEmpty())
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}