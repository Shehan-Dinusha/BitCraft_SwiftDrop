package com.swiftdrop.order.exception;

import com.swiftdrop.contracts.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("A persistence-time ConstraintViolationException is a server error, not a client 400")
    void constraintViolationIsServerError() {
        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(
                new ConstraintViolationException("internal detail", Set.of()), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
