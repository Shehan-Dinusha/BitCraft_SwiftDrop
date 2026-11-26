package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Parcels the courier loads at the hub before a run, or hands in there after it.")
public record LoadParcelsRequest(
        @NotEmpty(message = "Choose at least one parcel")
        @Size(max = 200, message = "Choose at most 200 parcels at once")
        @Schema(description = "Delivery session UUIDs")
        List<@NotNull UUID> deliveryIds
) {
}
