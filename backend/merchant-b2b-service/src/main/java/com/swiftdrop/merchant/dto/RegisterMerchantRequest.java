package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.domain.entity.BusinessCategory;
import com.swiftdrop.merchant.domain.entity.MonthlyParcelVolume;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * A business application. The registration number and the website are each optional, but the service
 * requires at least one of them so the dispatcher has something to verify. Messages are fixed strings so
 * a rejected value is never echoed back.
 */
@Schema(description = "Business application; owner identity comes from the authenticated gateway")
public record RegisterMerchantRequest(
        @NotBlank @Size(min = 2, max = 150)
        @Pattern(regexp = BUSINESS_NAME, message = NOT_ALLOWED)
        @Schema(description = "Business name", example = "Lanka Crafts")
        String businessName,

        @NotNull @Schema(description = "What the business sells")
        BusinessCategory businessCategory,

        @NotNull @Schema(description = "Rough number of parcels a month")
        MonthlyParcelVolume monthlyParcelVolume,

        @Size(max = 64) @Pattern(regexp = REG_NO, message = NOT_ALLOWED)
        @Schema(description = "Business registration number, if registered. Required when websiteUrl is empty.",
                example = "PV 00123456")
        String businessRegNo,

        @Size(max = 255) @Pattern(regexp = URL, message = "Must be a web address such as https://facebook.com/yourshop")
        @Schema(description = "Website or social media page. Required when businessRegNo is empty.",
                example = "https://facebook.com/lankacrafts")
        String websiteUrl,

        @NotBlank @Email @Size(max = 100)
        @Schema(description = "Business email", example = "orders@lankacrafts.lk")
        String contactEmail,

        @NotBlank @Pattern(regexp = PHONE, message = "Must be a valid Sri Lankan phone number")
        @Schema(description = "Business phone: 0771234567 or +94771234567", example = "+94771234567")
        String contactPhone,

        @NotBlank @Size(min = 5, max = 255) @Pattern(regexp = ADDRESS, message = NOT_ALLOWED)
        @Schema(description = "Pickup street address", example = "No. 12, Temple Road")
        String pickupAddress,

        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = CITY, message = NOT_ALLOWED)
        @Schema(description = "Pickup city", example = "Nugegoda")
        String pickupCity,

        @NotBlank @Pattern(regexp = "^\\d{5}$", message = "Must be a 5-digit postal code")
        @Schema(description = "Pickup postal code", example = "10250")
        String pickupPostalCode,

        @NotNull @Schema(description = "Pickup district; its dispatcher reviews the application")
        SriLankanDistrict operatingDistrict
) {
    static final String NOT_ALLOWED = "Contains characters that are not allowed";
    /** Letters (any script), digits, spaces and common business punctuation; no HTML metacharacters. */
    static final String BUSINESS_NAME = "^[\\p{L}\\p{M}\\p{N} .,&'’()/+#!@-]*$";
    static final String REG_NO = "^[A-Za-z0-9 ./-]*$";
    /** Optional scheme, a host with a dot, then an optional path; no spaces or angle brackets. */
    static final String URL = "^$|^(https?://)?[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(:\\d+)?(/[^\\s<>\"]*)?$";
    static final String PHONE = "^(0\\d{9}|\\+94\\d{9})$";
    static final String ADDRESS = "^[\\p{L}\\p{M}\\p{N} .,/#()&'’:-]*$";
    static final String CITY = "^[\\p{L}\\p{M}\\p{N} .'’-]*$";
}
