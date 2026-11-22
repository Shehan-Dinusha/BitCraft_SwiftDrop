package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.CashHandover;
import com.swiftdrop.delivery.domain.enums.CashHandoverStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Cash a courier handed to their dispatcher.")
public record CashHandoverResponse(
        @Schema(description = "Handover UUID") UUID handoverId,
        @Schema(description = "Courier UUID") UUID courierId,
        @Schema(description = "Courier name") String courierName,
        @Schema(description = "District of the hub that receives it") SriLankanDistrict district,
        @Schema(description = "What the courier collected", example = "1340.00") BigDecimal expectedAmount,
        @Schema(description = "What the dispatcher counted; null until counted", example = "1340.00") BigDecimal countedAmount,
        @Schema(description = "Counted minus expected: negative is a shortage, positive an excess; null until counted",
                example = "0.00") BigDecimal difference,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "SUBMITTED or COUNTED") CashHandoverStatus status,
        @Schema(description = "Why the amounts differ") String note,
        @Schema(description = "When the courier submitted it") Instant submittedAt,
        @Schema(description = "When the dispatcher counted it") Instant countedAt,
        @Schema(description = "The deposit that banked it") UUID depositId
) {
    public static CashHandoverResponse of(CashHandover h) {
        return new CashHandoverResponse(h.getHandoverId(), h.getCourierId(), h.getCourierName(), h.getDistrict(),
                h.getExpectedAmount(), h.getCountedAmount(),
                h.getCountedAmount() == null ? null : h.getCountedAmount().subtract(h.getExpectedAmount()),
                h.getCurrency(), h.getStatus(), h.getNote(), h.getSubmittedAt(), h.getCountedAt(), h.getDepositId());
    }
}
