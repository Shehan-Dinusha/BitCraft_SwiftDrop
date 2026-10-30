package com.swiftdrop.order.service.pricing;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.exception.PricingRequestRejectedException;
import com.swiftdrop.order.exception.PricingUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

/**
 * Gets fee estimates from the pricing service ({@code POST /api/v1/pricing/quote}).
 *
 * <p>There is deliberately no fallback to local pricing: if the pricing service cannot answer, the caller
 * gets {@link PricingUnavailableException}. Request bodies hold addresses' districts and parcel data, so
 * nothing from the request or response is logged; only the status code or the exception type is.</p>
 */
@Slf4j
public class HttpPricingClient implements PricingClient {

    static final String QUOTE_PATH = "/api/v1/pricing/quote";

    private final RestClient restClient;

    public HttpPricingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public FeeEstimateResponse estimate(FeeEstimateRequest request) {
        ApiResponse<FeeEstimateResponse> body;
        try {
            body = restClient.post()
                    .uri(QUOTE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("Pricing service rejected the quote request: status={}", res.getStatusCode().value());
                        throw new PricingRequestRejectedException();
                    })
                    .body(new ParameterizedTypeReference<ApiResponse<FeeEstimateResponse>>() { });
        } catch (PricingRequestRejectedException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("Pricing service failed: status={}", e.getStatusCode().value());
            throw new PricingUnavailableException(e);
        } catch (RestClientException e) {
            // Timeouts, refused connections and unreadable bodies. The message can contain the URL, so only the type is logged.
            log.warn("Pricing service unreachable or unreadable: {}", e.getClass().getSimpleName());
            throw new PricingUnavailableException(e);
        }

        if (body == null || !body.success() || body.data() == null || body.data().getEstimatedFee() == null) {
            log.warn("Pricing service returned an empty or invalid quote");
            throw new PricingUnavailableException();
        }
        return body.data();
    }
}
