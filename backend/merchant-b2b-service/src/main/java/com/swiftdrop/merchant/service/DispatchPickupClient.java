package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.merchant.dto.MerchantPickupState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Asks dispatch, which holds the pickup queue, about a shop's parcels. */
@Component
public class DispatchPickupClient {

    private final RestClient client;

    @Autowired
    public DispatchPickupClient(RestClient.Builder builder, @Value("${swiftdrop.dispatch-service-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    DispatchPickupClient(RestClient client) {
        this.client = client;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parcel(UUID orderId, String trackingNumber, MerchantPickupState state, String courierName, Instant readyAt) {
    }

    public List<Parcel> atShop(UUID merchantId) {
        return data(() -> client.get().uri("/internal/v1/merchant-pickups/{id}", merchantId).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<Parcel>>>() { }));
    }

    public List<Parcel> markReady(UUID merchantId, String merchantName) {
        return data(() -> client.post().uri("/internal/v1/merchant-pickups/{id}/ready", merchantId)
                .body(Map.of("merchantName", merchantName)).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<Parcel>>>() { }));
    }

    private static List<Parcel> data(java.util.function.Supplier<ApiResponse<List<Parcel>>> call) {
        try {
            ApiResponse<List<Parcel>> body = call.get();
            if (body != null && body.success() && body.data() != null) {
                return body.data();
            }
        } catch (RestClientException ex) {
            // answered below
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pickups are not available right now. Try again in a moment.");
    }
}
