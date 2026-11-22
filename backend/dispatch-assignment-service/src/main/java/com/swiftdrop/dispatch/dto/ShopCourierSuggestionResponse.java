package com.swiftdrop.dispatch.dto;

import com.swiftdrop.dispatch.domain.enums.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "A courier on shift and the parcels of the shop their vehicle has room for.")
public record ShopCourierSuggestionResponse(
        @Schema(description = "Courier UUID") UUID courierId,
        @Schema(description = "Courier name") String courierName,
        @Schema(description = "Vehicle") VehicleType vehicleType,
        @Schema(description = "Parcels held now", example = "3") int activeParcels,
        @Schema(description = "Parcels the courier can hold", example = "25") int maxCapacity,
        @Schema(description = "The parcels that fit together, longest waiting first; empty when none do")
        List<UUID> fittingOrderIds
) {
}
