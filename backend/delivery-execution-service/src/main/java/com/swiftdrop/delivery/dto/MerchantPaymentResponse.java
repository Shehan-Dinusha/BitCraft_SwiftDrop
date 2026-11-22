package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.MerchantPayment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "What a shop paid at one pickup visit.")
public record MerchantPaymentResponse(
        @Schema(description = "Payment UUID") UUID paymentId,
        @Schema(description = "Shop name", example = "Nimal Fashion") String merchantName,
        @Schema(description = "Courier who collected it") String courierName,
        @Schema(description = "District of the pickup") SriLankanDistrict district,
        @Schema(description = "Amount", example = "4650.00") BigDecimal amount,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "Parcels it paid for", example = "11") int parcels,
        @Schema(description = "When") Instant paidAt
) {
    public static MerchantPaymentResponse of(MerchantPayment p) {
        return new MerchantPaymentResponse(p.getPaymentId(), p.getMerchantName(), p.getCourierName(), p.getDistrict(),
                p.getAmount(), p.getCurrency(), p.getParcels(), p.getPaidAt());
    }
}
