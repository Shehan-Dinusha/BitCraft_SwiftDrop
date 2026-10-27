package com.swiftdrop.dispatch.dto;

import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to update courier shift status.")
public record UpdateShiftStatusRequest(
        @NotNull(message = "Courier ID is required")
        @Schema(description = "Courier UUID")
        UUID courierId,

        @NotNull(message = "Shift status is required")
        @Schema(description = "New shift status", example = "AVAILABLE")
        ShiftStatus status
) {}
