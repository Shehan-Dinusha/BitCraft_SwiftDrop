package com.swiftdrop.delivery.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.delivery.dto.CourierJobStop;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.exception.OrderDetailsUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads an order's addresses, contacts and cash amounts from the order service, which owns them. Nothing
 * is stored here. Errors never carry the order service's body, because it can hold personal data.
 */
@Component
public class OrderJobClient {

    private final RestClient client;

    @Autowired
    public OrderJobClient(RestClient.Builder builder, @Value("${swiftdrop.order-service-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    OrderJobClient(RestClient client) {
        this.client = client;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderJob(
            boolean cancelled,
            OrderSpeed speed,
            CourierJobStop pickup,
            CourierJobStop dropOff,
            String notes,
            String itemDescription,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal fee,
            BigDecimal codAmount,
            String currency
    ) {
    }

    public OrderJob get(UUID orderId) {
        try {
            ApiResponse<OrderJob> body = client.get()
                    .uri("/internal/v1/orders/{orderId}/courier-job", orderId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<OrderJob>>() { });
            if (body == null || !body.success() || body.data() == null) {
                throw new OrderDetailsUnavailableException();
            }
            return body.data();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new DeliveryNotFoundException("Order not found for this delivery");
        } catch (RestClientException ex) {
            throw new OrderDetailsUnavailableException();
        }
    }
}
