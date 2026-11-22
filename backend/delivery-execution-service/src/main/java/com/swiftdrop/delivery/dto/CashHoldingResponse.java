package com.swiftdrop.delivery.dto;

import com.swiftdrop.delivery.domain.repository.CashEntryRepository.CourierHolding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A courier who holds cash they have not handed over yet.")
public record CashHoldingResponse(
        @Schema(description = "Courier UUID") UUID courierId,
        @Schema(description = "Courier name") String courierName,
        @Schema(description = "Amount", example = "3170.00") BigDecimal amount,
        @Schema(description = "Parcels it came from", example = "4") long parcels,
        @Schema(description = "When the oldest of it was collected") Instant since
) {
    public static CashHoldingResponse of(CourierHolding h) {
        return new CashHoldingResponse(h.getCourierId(), h.getCourierName(), h.getAmount(), h.getParcels(), h.getSince());
    }
}
