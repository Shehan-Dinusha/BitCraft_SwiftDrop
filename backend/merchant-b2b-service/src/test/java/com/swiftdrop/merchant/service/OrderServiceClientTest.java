package com.swiftdrop.merchant.service;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OrderServiceClientTest {
    MockRestServiceServer server;
    OrderServiceClient client;
    UUID correlation = UUID.randomUUID();
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://order-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrderServiceClient(builder.build());
    }

    @Test void usesInternalEndpointAndPropagatesCorrelation() {
        UUID orderId = UUID.randomUUID();
        server.expect(requestTo("http://order-service/internal/v1/orders"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-Correlation-Id", correlation.toString()))
                .andExpect(content().json("{\"externalRef\":\"SHOP-001\"}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":true,\"data\":{\"orderId\":\"" + orderId + "\",\"trackingNumber\":\"SD-ABCD-EFGH\",\"status\":\"CREATED\"}}"));
        var result = client.create(Map.of("externalRef", "SHOP-001"), correlation);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.created()).isTrue();
        server.verify();
    }

    @Test void rejectsMalformedSuccessAndHidesDownstreamErrorBodies() {
        server.expect(anything()).andRespond(withSuccess("{\"success\":true,\"data\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.create(Map.of(), correlation)).hasMessageContaining("502");
        server.verify();
    }

    @Test void downstreamValidationIsAClientError() {
        server.expect(anything()).andRespond(withBadRequest().body("sensitive rejected input"));
        assertThatThrownBy(() -> client.create(Map.of(), correlation))
                .hasMessageContaining("400").hasMessageNotContaining("sensitive");
        server.verify();
    }
}
