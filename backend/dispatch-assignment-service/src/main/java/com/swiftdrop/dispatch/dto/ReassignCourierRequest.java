package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to intentionally move an assigned order to a different courier.")
public record ReassignCourierRequest(
        @NotNull(message = "Order ID is required")
        @Schema(description = "Order UUID to reassign")
        UUID orderId,

        @NotNull(message = "Expected current courier ID is required")
        @Schema(description = "Courier the dispatcher believes is currently assigned. "
                + "The request is rejected with 409 if the assignment has changed since it was viewed.")
        UUID expectedCurrentCourierId,

        @NotNull(message = "New courier ID is required")
        @Schema(description = "Courier UUID that should take over the order")
        UUID newCourierId,

        @Schema(description = "Identifier or username of dispatcher performing the reassignment", example = "DISPATCHER_ADMIN")
        String assignedBy,

        @Schema(description = "Optional reason or note for the reassignment")
        String reason
) {}
