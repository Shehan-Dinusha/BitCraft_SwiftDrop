package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;


@Schema(description = "Request to advance delivery finite state machine.")
public record TransitionDeliveryRequest(
        @NotNull(message = "Target state is required")
        @Schema(description = "Destination delivery state", example = "PICKED_UP")
        DeliveryState targetState,

        @Schema(description = "Optional operational notes or handover remarks", example = "Parcel inspected and collected")
        String notes,

        @Schema(description = "Failure reason if target state is FAILED")
        DeliveryFailureReason failureReason,

        @Schema(description = "Actor role executing transition", example = "COURIER")
        String actorRole
) {}
