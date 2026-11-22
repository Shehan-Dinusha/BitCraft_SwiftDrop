package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.CashDeposit;
import com.swiftdrop.delivery.domain.enums.CashDepositStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Cash a hub banked.")
public record CashDepositResponse(
        @Schema(description = "Deposit UUID") UUID depositId,
        @Schema(description = "District of the hub") SriLankanDistrict district,
        @Schema(description = "Amount banked", example = "25400.00") BigDecimal amount,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "Bank slip number", example = "BOC-884213") String reference,
        @Schema(description = "SUBMITTED, CONFIRMED or REJECTED") CashDepositStatus status,
        @Schema(description = "When it was banked") Instant depositedAt,
        @Schema(description = "When an admin confirmed or rejected it") Instant reviewedAt,
        @Schema(description = "Why it was rejected") String reviewNote
) {
    public static CashDepositResponse of(CashDeposit d) {
        return new CashDepositResponse(d.getDepositId(), d.getDistrict(), d.getAmount(), d.getCurrency(),
                d.getReference(), d.getStatus(), d.getDepositedAt(), d.getReviewedAt(), d.getReviewNote());
    }
}
