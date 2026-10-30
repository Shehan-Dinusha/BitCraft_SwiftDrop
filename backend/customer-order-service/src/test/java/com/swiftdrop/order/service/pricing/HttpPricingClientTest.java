package com.swiftdrop.order.service.pricing;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.config.PricingClientConfig;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.exception.PricingRequestRejectedException;
import com.swiftdrop.order.exception.PricingUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPricingClientTest {

    private static final String BASE_URL = "http://pricing.test";
    private static final String QUOTE_URL = BASE_URL + "/api/v1/pricing/quote";

    private static final String QUOTE_JSON = """
            {
              "success": true,
              "message": "Quotation calculated successfully",
              "timestamp": "2026-10-30T10:15:30Z",
              "data": {
                "pickupDistrict": "COLOMBO",
                "deliveryDistrict": "KANDY",
                "pickupZone": "ZONE_WESTERN",
                "deliveryZone": "ZONE_CENTRAL",
                "intraDistrict": false,
                "actualWeightKg": 2.40,
                "volumetricWeightKg": 1.80,
                "chargeableWeightKg": 2.40,
                "speed": "EXPRESS",
                "baseFee": 550.00,
                "weightFee": 240.00,
                "speedSurcharge": 395.00,
                "estimatedFee": 1185.00,
                "currency": "LKR",
                "pricingRuleVersionId": "PRICING-2026-V1",
                "somethingNew": "ignored"
              }
            }
            """;

    private MockRestServiceServer server;
    private HttpPricingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPricingClient(builder.build());
    }

    private FeeEstimateRequest request() {
        return FeeEstimateRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("2.4"))
                .lengthCm(new BigDecimal("30"))
                .widthCm(new BigDecimal("20"))
                .heightCm(new BigDecimal("15"))
                .speed(OrderSpeed.EXPRESS)
                .build();
    }

    @Test
    @DisplayName("Posts the estimate fields to the quote endpoint and maps every field of the response")
    void successMapsAllFields() {
        server.expect(requestTo(QUOTE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pickupDistrict").value("COLOMBO"))
                .andExpect(jsonPath("$.deliveryDistrict").value("KANDY"))
                .andExpect(jsonPath("$.actualWeightKg").value(2.4))
                .andExpect(jsonPath("$.lengthCm").value(30))
                .andExpect(jsonPath("$.speed").value("EXPRESS"))
                .andRespond(withSuccess(QUOTE_JSON, MediaType.APPLICATION_JSON));

        FeeEstimateResponse response = client.estimate(request());

        assertThat(response.getPickupDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(response.getDeliveryDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        assertThat(response.isIntraDistrict()).isFalse();
        assertThat(response.getActualWeightKg()).isEqualByComparingTo("2.40");
        assertThat(response.getVolumetricWeightKg()).isEqualByComparingTo("1.80");
        assertThat(response.getChargeableWeightKg()).isEqualByComparingTo("2.40");
        assertThat(response.getSpeed()).isEqualTo(OrderSpeed.EXPRESS);
        assertThat(response.getBaseFee()).isEqualByComparingTo("550.00");
        assertThat(response.getWeightFee()).isEqualByComparingTo("240.00");
        assertThat(response.getSpeedSurcharge()).isEqualByComparingTo("395.00");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("1185.00");
        assertThat(response.getCurrency()).isEqualTo("LKR");
        assertThat(response.getPricingRuleVersionId()).isEqualTo("PRICING-2026-V1");
        server.verify();
    }

    @Test
    @DisplayName("A 4xx from pricing is a rejected-request error, not an outage, and is not retried")
    void clientErrorIsMappedToRejected() {
        server.expect(requestTo(QUOTE_URL))
                .andRespond(withBadRequest()
                        .body("{\"message\":\"Actual weight must be at least 0.01 kg\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.estimate(request()))
                .isInstanceOf(PricingRequestRejectedException.class)
                .hasMessageNotContaining("weight");
        server.verify();
    }

    @Test
    @DisplayName("5xx responses map to PricingUnavailableException without the downstream body")
    void serverErrorIsMappedToUnavailable() {
        server.expect(requestTo(QUOTE_URL))
                .andRespond(withServerError().body("stack trace with secret-downstream-detail"));

        assertThatThrownBy(() -> client.estimate(request()))
                .isInstanceOf(PricingUnavailableException.class)
                .hasMessage("Pricing service is temporarily unavailable")
                .hasMessageNotContaining("secret-downstream-detail");
    }

    @Test
    @DisplayName("A 503 from pricing maps to PricingUnavailableException")
    void serviceUnavailableIsMappedToUnavailable() {
        server.expect(requestTo(QUOTE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.estimate(request())).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    @DisplayName("An I/O failure maps to PricingUnavailableException and does not leak the URL")
    void ioFailureIsMappedToUnavailable() {
        server.expect(requestTo(QUOTE_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.estimate(request()))
                .isInstanceOf(PricingUnavailableException.class)
                .hasMessageNotContaining("pricing.test");
    }

    @Test
    @DisplayName("Empty, unsuccessful, data-less or unparseable bodies are treated as unavailable")
    void unusableBodiesAreMappedToUnavailable() {
        String[] bodies = {
                "",
                "{}",
                "{\"success\":false,\"message\":\"nope\"}",
                "{\"success\":true}",
                "{\"success\":true,\"data\":{\"pickupDistrict\":\"COLOMBO\"}}",
                "this is not json",
        };
        for (String body : bodies) {
            server.reset();
            server.expect(requestTo(QUOTE_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.estimate(request()))
                    .as("body [%s]", body)
                    .isInstanceOf(PricingUnavailableException.class);
        }
    }

    @Test
    @DisplayName("A read timeout against a slow server maps to PricingUnavailableException")
    void readTimeoutIsMappedToUnavailable() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(1500);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException | IOException ignored) {
                // the client has already given up
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            RestClient restClient = PricingClientConfig.buildRestClient(RestClient.builder(),
                    "http://127.0.0.1:" + slow.getAddress().getPort(), 500, 300);
            HttpPricingClient realClient = new HttpPricingClient(restClient);

            long started = System.nanoTime();
            assertThatThrownBy(() -> realClient.estimate(request())).isInstanceOf(PricingUnavailableException.class);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertThat(elapsedMs).as("fails at the read timeout, not after the slow response").isLessThan(1200);
        } finally {
            slow.stop(0);
        }
    }

    @Test
    @DisplayName("A refused connection maps to PricingUnavailableException")
    void connectionRefusedIsMappedToUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RestClient restClient = PricingClientConfig.buildRestClient(RestClient.builder(),
                "http://127.0.0.1:" + closedPort, 500, 300);

        assertThatThrownBy(() -> new HttpPricingClient(restClient).estimate(request()))
                .isInstanceOf(PricingUnavailableException.class);
    }
}
