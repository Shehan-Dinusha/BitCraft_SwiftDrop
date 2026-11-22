package com.swiftdrop.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Orders a courier collects at one stop.")
public record InternalCourierJobsRequest(
        @NotEmpty @Size(max = 200) @Schema(description = "Order UUIDs") List<@NotNull UUID> orderIds
) {
}
