package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to manually assign or override a courier assignment for an order.")
public record ManualAssignRequest(
        @NotNull(message = "Order ID is required")
        @Schema(description = "Order UUID to assign")
        UUID orderId,

        @NotNull(message = "Courier ID is required")
        @Schema(description = "Courier UUID to assign")
        UUID courierId,

        @Schema(description = "Identifier or username of dispatcher performing override", example = "DISPATCHER_ADMIN")
        String assignedBy,

        @Schema(description = "Optional reason or note for assignment override")
        String reason
) {}
