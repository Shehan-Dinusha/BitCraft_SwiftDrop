package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.merchant.dto.B2bOrderResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderServiceClient {
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderServiceClient(RestClient.Builder builder, @Value("${swiftdrop.order-service-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    OrderServiceClient(RestClient client) { this.client = client; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderIdentity(UUID orderId, String trackingNumber) {}

    public B2bOrderResult create(Map<String, Object> body, UUID correlationId) {
        try {
            var response = client.post().uri("/internal/v1/orders")
                    .header(JwtHeaderConstants.X_CORRELATION_ID, correlationId.toString())
                    .body(body).retrieve().toEntity(new ParameterizedTypeReference<ApiResponse<OrderIdentity>>() {});
            var envelope = response.getBody();
            if (envelope == null || !envelope.success() || envelope.data() == null
                    || envelope.data().orderId() == null || envelope.data().trackingNumber() == null
                    || envelope.data().trackingNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid order-service response");
            }
            return new B2bOrderResult((String) body.get("externalRef"), envelope.data().orderId(),
                    envelope.data().trackingNumber(), response.getStatusCode().value() == 201, null);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 422) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order service rejected the order details");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order service could not accept the order; retry with the same reference");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order service unavailable; retry with the same reference");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid order-service response");
        }
    }
}
