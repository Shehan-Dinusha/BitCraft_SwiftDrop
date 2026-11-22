package com.swiftdrop.dispatch.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.dispatch.exception.ShiftEndNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/** Asks the delivery service, which keeps the cash ledger, how much cash a courier still holds. */
@Component
public class CourierCashClient {

    private final RestClient client;

    @Autowired
    public CourierCashClient(RestClient.Builder builder, @Value("${swiftdrop.delivery-service-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    CourierCashClient(RestClient client) {
        this.client = client;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CashInHand(BigDecimal inHand) {
    }

    /** Fails closed: without an answer the shift stays open, so no cash is left behind unseen. */
    public BigDecimal inHand(UUID courierId) {
        try {
            ApiResponse<CashInHand> body = client.get()
                    .uri("/internal/v1/cash/couriers/{courierId}/in-hand", courierId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<CashInHand>>() { });
            if (body != null && body.success() && body.data() != null && body.data().inHand() != null) {
                return body.data().inHand();
            }
        } catch (RestClientException ex) {
            // answered below
        }
        throw new ShiftEndNotAllowedException("Your cash could not be checked. Try again in a moment.");
    }
}
