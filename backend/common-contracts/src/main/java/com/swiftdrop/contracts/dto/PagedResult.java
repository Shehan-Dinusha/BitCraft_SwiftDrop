package com.swiftdrop.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Generic paginated result wrapper for list endpoints across all services.
 *
 * @param <T> the type of elements in the page
 */
@Schema(description = "Standard paginated result wrapper.")
public record PagedResult<T>(

        @JsonProperty("content")
        @Schema(description = "Items in the requested page.")
        List<T> content,

        @JsonProperty("page")
        @Schema(description = "Zero-based page index.", example = "0")
        int page,

        @JsonProperty("size")
        @Schema(description = "Requested page size.", example = "20")
        int size,

        @JsonProperty("totalElements")
        @Schema(description = "Total number of matching items.", example = "125")
        long totalElements,

        @JsonProperty("totalPages")
        @Schema(description = "Total number of available pages.", example = "7")
        int totalPages,

        @JsonProperty("hasNext")
        @Schema(description = "Whether a subsequent page exists.", example = "true")
        boolean hasNext,

        @JsonProperty("hasPrevious")
        @Schema(description = "Whether a preceding page exists.", example = "false")
        boolean hasPrevious

) {
    /**
     * Factory method to create a PagedResult from Spring Data Page metadata.
     */
    public static <T> PagedResult<T> of(List<T> content, int page, int size,
                                         long totalElements, int totalPages) {
        return new PagedResult<>(
                content, page, size, totalElements, totalPages,
                page < totalPages - 1,
                page > 0
        );
    }
}
