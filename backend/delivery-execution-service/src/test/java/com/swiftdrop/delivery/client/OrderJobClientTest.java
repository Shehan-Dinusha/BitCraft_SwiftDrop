package com.swiftdrop.delivery.client;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.exception.OrderDetailsUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrderJobClientTest {

    private MockRestServiceServer server;
    private OrderJobClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://order-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrderJobClient(builder.build());
    }

    @Test
    @DisplayName("Reads the order's job from the internal endpoint and ignores fields it does not know")
    void readsTheJob() {
        UUID orderId = UUID.randomUUID();
        server.expect(requestTo("http://order-service/internal/v1/orders/" + orderId + "/courier-job"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"orderId":"%s","cancelled":false,"speed":"EXPRESS",
                         "pickup":{"name":"Nimal","phone":"+94771234567","city":"Colombo 03","district":"COLOMBO","extra":1},
                         "fee":670.00,"codAmount":0.00,"currency":"LKR","somethingNew":true}}
                        """.formatted(orderId), MediaType.APPLICATION_JSON));

        OrderJobClient.OrderJob job = client.get(orderId);

        assertThat(job.pickup().name()).isEqualTo("Nimal");
        assertThat(job.pickup().district()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(job.fee()).isEqualByComparingTo("670.00");
        server.verify();
    }

    @Test
    @DisplayName("An unknown order is not found")
    void unknownOrder() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.get(UUID.randomUUID())).isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    @DisplayName("A failing or empty answer is reported as unavailable, without the order service's body")
    void failuresHideTheBody() {
        server.expect(anything()).andRespond(withServerError().body("Nimal +94771234567"));

        assertThatThrownBy(() -> client.get(UUID.randomUUID()))
                .isInstanceOf(OrderDetailsUnavailableException.class)
                .hasMessageNotContaining("Nimal")
                .hasNoCause();

        server.reset();
        server.expect(anything()).andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.get(UUID.randomUUID())).isInstanceOf(OrderDetailsUnavailableException.class);
    }
}
