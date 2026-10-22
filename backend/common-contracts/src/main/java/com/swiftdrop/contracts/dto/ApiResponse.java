package com.swiftdrop.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Standard API response envelope used across all SwiftDrop microservices.
 *
 * @param <T> the type of the response payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard successful or business-error response envelope.")
public record ApiResponse<T>(

        @JsonProperty("success")
        @Schema(description = "Whether the request completed successfully.", example = "true")
        boolean success,

        @JsonProperty("message")
        @Schema(description = "Human-readable result message.", example = "Request completed successfully")
        String message,

        @JsonProperty("data")
        @Schema(description = "Response payload. The schema depends on the endpoint.")
        T data,

        @JsonProperty("timestamp")
        @Schema(description = "UTC time at which the response was created.", example = "2026-01-01T12:00:00Z")
        Instant timestamp

) {
    public ApiResponse {
        if (timestamp == null) timestamp = Instant.now();
    }

    /**
     * Factory method for successful responses with data payload.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    /**
     * Factory method for successful responses without data.
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, Instant.now());
    }

    /**
     * Factory method for error responses.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
