package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Give a shop's parcels to one courier. All of them are assigned, or none.")
public record AssignShopPickupRequest(
        @NotNull(message = "Choose a courier") @Schema(description = "Courier UUID") UUID courierId,

        @NotEmpty(message = "Choose at least one parcel")
        @Size(max = 200, message = "Assign at most 200 parcels at once")
        @Schema(description = "Order UUIDs") List<@NotNull UUID> orderIds
) {
}
