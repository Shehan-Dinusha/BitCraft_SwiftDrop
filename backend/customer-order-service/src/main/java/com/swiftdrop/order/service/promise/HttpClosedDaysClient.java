package com.swiftdrop.order.service.promise;

import com.swiftdrop.contracts.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the holidays from {@code GET /internal/v1/calendar/closed-days} and the working-day rules from
 * {@code GET /internal/v1/calendar/rules} on the dispatch service. A booking never fails because the calendar
 * is down: the promise then uses the standard working week without holidays, and this is logged.
 */
@Slf4j
public class HttpClosedDaysClient implements ClosedDaysClient {

    static final String PATH = "/internal/v1/calendar/closed-days";
    static final String RULES_PATH = "/internal/v1/calendar/rules";

    private final RestClient restClient;

    public HttpClosedDaysClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Set<LocalDate> between(LocalDate from, LocalDate to) {
        try {
            ApiResponse<List<LocalDate>> body = restClient.get()
                    .uri(uri -> uri.path(PATH).queryParam("from", from).queryParam("to", to).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<List<LocalDate>>>() { });
            if (body != null && body.data() != null) {
                return new HashSet<>(body.data());
            }
            log.warn("The dispatch calendar returned no holidays; counting Sundays only");
        } catch (RestClientException e) {
            log.warn("The dispatch calendar is unreachable ({}); counting Sundays only", e.getClass().getSimpleName());
        }
        return Set.of();
    }

    @Override
    public WorkingRules rules() {
        try {
            ApiResponse<WorkingRules> body = restClient.get()
                    .uri(RULES_PATH)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<WorkingRules>>() { });
            if (body != null && body.data() != null && body.data().sameDayCutoff() != null
                    && body.data().pickupCutoff() != null) {
                return body.data();
            }
            log.warn("The dispatch calendar returned no working-day rules; using the standard ones");
        } catch (RestClientException e) {
            log.warn("The dispatch calendar rules are unreachable ({}); using the standard ones",
                    e.getClass().getSimpleName());
        }
        return WorkingRules.STANDARD;
    }
}
