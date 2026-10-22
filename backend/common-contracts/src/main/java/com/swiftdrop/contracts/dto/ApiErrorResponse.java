package com.swiftdrop.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardised error response envelope for all SwiftDrop APIs.
 * Supports field-level validation errors and trace correlation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response with optional validation and correlation details.")
public record ApiErrorResponse(

        @JsonProperty("status")
        @Schema(description = "HTTP status code.", example = "400")
        int status,

        @JsonProperty("error")
        @Schema(description = "HTTP error reason.", example = "Bad Request")
        String error,

        @JsonProperty("message")
        @Schema(description = "Human-readable error message.", example = "Invalid request")
        String message,

        @JsonProperty("path")
        @Schema(description = "Request path that produced the error.", example = "/api/v1/orders")
        String path,

        @JsonProperty("correlationId")
        @Schema(description = "Correlation identifier for tracing the failure.", example = "0f5e8c4a-2c6d-4a58-9c8f-4c2c4e7b8a11")
        String correlationId,

        @JsonProperty("fieldErrors")
        @Schema(description = "Validation messages keyed by field.")
        Map<String, List<String>> fieldErrors,

        @JsonProperty("timestamp")
        @Schema(description = "UTC time at which the error was created.", example = "2026-01-01T12:00:00Z")
        Instant timestamp

) {
    public ApiErrorResponse {
        if (timestamp == null) timestamp = Instant.now();
    }

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(status, error, message, path, null, null, Instant.now());
    }

    public static ApiErrorResponse withValidation(int status, String error, String message,
                                                   String path, Map<String, List<String>> fieldErrors) {
        return new ApiErrorResponse(status, error, message, path, null, fieldErrors, Instant.now());
    }
}
