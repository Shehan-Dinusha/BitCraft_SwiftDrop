package com.swiftdrop.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "The orders to print shipping labels for, one page each.")
public record ShippingLabelsRequest(
        @NotEmpty(message = "Choose at least one order")
        @Size(max = 100, message = "Print at most 100 labels at once")
        @Schema(description = "Order UUIDs")
        List<@NotNull UUID> orderIds
) {
}
