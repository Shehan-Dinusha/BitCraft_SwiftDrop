package com.swiftdrop.notification.exception;

import com.swiftdrop.contracts.dto.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The catch-all handler must keep Spring MVC's own 4xx statuses and never leak exception messages. */
class FrameworkErrorStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("An unknown path is a 404, not a 500")
    void unknownPathIsNotFound() {
        ResponseEntity<ApiErrorResponse> response = handler.handleGeneral(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/does-not-exist"), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("A wrong HTTP method is a 405 with an Allow header")
    void wrongMethodIsMethodNotAllowed() {
        ResponseEntity<ApiErrorResponse> response = handler.handleGeneral(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET")), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("GET");
    }

    @Test
    @DisplayName("A missing required query parameter is a 400")
    void missingParameterIsBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleGeneral(
                new MissingServletRequestParameterException("date", "String"), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("An unexpected failure is a 500 that does not echo the exception message")
    void unexpectedFailureHidesDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleGeneral(
                new IllegalStateException("duplicate key value violates unique constraint, phone=0771234567"),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("0771234567");
    }
}
