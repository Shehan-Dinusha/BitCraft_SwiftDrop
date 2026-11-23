package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "The parcels the courier loads at the hub before leaving on their run.")
public record LoadParcelsRequest(
        @NotEmpty(message = "Choose at least one parcel")
        @Size(max = 200, message = "Load at most 200 parcels at once")
        @Schema(description = "Delivery session UUIDs waiting at the hub for this courier")
        List<@NotNull UUID> deliveryIds
) {
}
