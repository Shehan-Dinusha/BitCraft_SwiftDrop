package com.swiftdrop.delivery.dto;

import com.swiftdrop.delivery.domain.entity.CashEntry;
import com.swiftdrop.delivery.domain.enums.CashEntryType;
import com.swiftdrop.delivery.domain.enums.CashKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Cash collected or refunded for one parcel.")
public record CashEntryResponse(
        @Schema(description = "Entry UUID") UUID entryId,
        @Schema(description = "COLLECTION or REFUND") CashEntryType entryType,
        @Schema(description = "DELIVERY_FEE (revenue) or COD (the merchant's money)") CashKind kind,
        @Schema(description = "Tracking number", example = "SD-7K4M-9QX2") String trackingNumber,
        @Schema(description = "Amount", example = "670.00") BigDecimal amount,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "Why, for a refund") String note,
        @Schema(description = "When it was recorded") Instant recordedAt
) {
    public static CashEntryResponse of(CashEntry entry) {
        return new CashEntryResponse(entry.getEntryId(), entry.getEntryType(), entry.getKind(), entry.getTrackingNumber(),
                entry.getAmount(), entry.getCurrency(), entry.getNote(), entry.getRecordedAt());
    }
}
