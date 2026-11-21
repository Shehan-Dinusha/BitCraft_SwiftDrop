package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.domain.entity.BusinessCategory;
import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.domain.entity.MonthlyParcelVolume;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Business application and approval audit information")
public record MerchantApprovalResponse(
        @Schema(description = "Merchant ID") UUID merchantId,
        @Schema(description = "Business owner ID") UUID ownerUserId,
        @Schema(description = "Business name") String businessName,
        @Schema(description = "What the business sells") BusinessCategory businessCategory,
        @Schema(description = "Rough number of parcels a month") MonthlyParcelVolume monthlyParcelVolume,
        @Schema(description = "Registration number, if given") String businessRegNo,
        @Schema(description = "Website or social media page, if given") String websiteUrl,
        @Schema(description = "Business email") String contactEmail,
        @Schema(description = "Business phone") String contactPhone,
        @Schema(description = "Pickup address") String pickupAddress,
        @Schema(description = "Pickup city") String pickupCity,
        @Schema(description = "Pickup postal code") String pickupPostalCode,
        @Schema(description = "Operating district") SriLankanDistrict operatingDistrict,
        @Schema(description = "Approval status") MerchantStatus status,
        @Schema(description = "Approving administrator") UUID approvedBy,
        @Schema(description = "Approval time") Instant approvedAt,
        @Schema(description = "Rejection reason") String rejectionReason,
        @Schema(description = "Application time") Instant createdAt
) {
    public static MerchantApprovalResponse from(MerchantAccount account) {
        return new MerchantApprovalResponse(account.getMerchantId(), account.getOwnerUserId(),
                account.getBusinessName(), account.getBusinessCategory(), account.getMonthlyParcelVolume(),
                account.getBusinessRegNo(), account.getWebsiteUrl(), account.getContactEmail(),
                account.getContactPhone(), account.getPickupAddress(), account.getPickupCity(),
                account.getPickupPostalCode(), account.getOperatingDistrict(),
                account.getStatus(), account.getApprovedBy(), account.getApprovedAt(),
                account.getRejectionReason(), account.getCreatedAt());
    }
}
