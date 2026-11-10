package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order created on behalf of a merchant (service-to-service, from the merchant service).
 *
 * <p>Uses the same shared patterns and limits as the customer {@link CreateOrderRequest}
 * ({@link ValidationPatterns}). Differences: the merchant id and an external reference are required,
 * the recipient email and the parcel dimensions are optional (dimensions default to
 * {@value #DEFAULT_DIMENSION_CM} cm), and there is no customer id: the merchant id is used as the
 * customer id.</p>
 */
@Schema(description = "Order created on behalf of a merchant. Idempotent on (merchantId, externalRef).")
public record InternalCreateOrderRequest(

        @NotNull(message = "Merchant id is mandatory")
        @Schema(description = "Merchant UUID. Also stored as the order's customer id.")
        UUID merchantId,

        @NotBlank(message = "External reference is mandatory")
        @Size(max = 128, message = "Must be at most 128 characters")
        @Pattern(regexp = ValidationPatterns.EXTERNAL_REF, message = ValidationPatterns.EXTERNAL_REF_MESSAGE)
        @Schema(description = "The merchant's own reference. Posting the same (merchantId, externalRef) again returns the original order.",
                example = "SHOP-2026-000123")
        String externalRef,

        @NotBlank(message = "Pickup address line 1 is mandatory")
        @Size(min = 5, max = 200, message = "Must be between 5 and 200 characters")
        @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
        @Schema(example = "No. 42, Galle Road")
        String pickupAddressLine1,

        @Size(max = 200, message = "Must be at most 200 characters")
        @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
        String pickupAddressLine2,

        @NotBlank(message = "Pickup city is mandatory")
        @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
        @Pattern(regexp = ValidationPatterns.CITY, message = ValidationPatterns.CITY_MESSAGE)
        @Schema(example = "Colombo 03")
        String pickupCity,

        @NotNull(message = "Pickup district is mandatory")
        SriLankanDistrict pickupDistrict,

        @NotBlank(message = "Pickup postal code is mandatory")
        @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = ValidationPatterns.POSTAL_CODE_MESSAGE)
        @Schema(example = "00300")
        String pickupPostalCode,

        @NotBlank(message = "Pickup contact name is mandatory")
        @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
        @Pattern(regexp = ValidationPatterns.NAME, message = ValidationPatterns.NAME_MESSAGE)
        String pickupContactName,

        @NotBlank(message = "Pickup contact phone is mandatory")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        @Schema(example = "+94771234567")
        String pickupContactPhone,

        @NotBlank(message = "Delivery address line 1 is mandatory")
        @Size(min = 5, max = 200, message = "Must be between 5 and 200 characters")
        @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
        String deliveryAddressLine1,

        @Size(max = 200, message = "Must be at most 200 characters")
        @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
        String deliveryAddressLine2,

        @NotBlank(message = "Delivery city is mandatory")
        @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
        @Pattern(regexp = ValidationPatterns.CITY, message = ValidationPatterns.CITY_MESSAGE)
        String deliveryCity,

        @NotNull(message = "Delivery district is mandatory")
        SriLankanDistrict deliveryDistrict,

        @NotBlank(message = "Delivery postal code is mandatory")
        @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = ValidationPatterns.POSTAL_CODE_MESSAGE)
        String deliveryPostalCode,

        @NotBlank(message = "Recipient name is mandatory")
        @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
        @Pattern(regexp = ValidationPatterns.NAME, message = ValidationPatterns.NAME_MESSAGE)
        String recipientName,

        @NotBlank(message = "Recipient phone is mandatory")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String recipientPhone,

        @Email(message = "Recipient email must be a valid email address")
        @Size(max = 128, message = "Must be at most 128 characters")
        @Schema(description = "Optional for merchant orders (the merchant is notified by webhook).")
        String recipientEmail,

        @DecimalMin(value = "0.1", message = "Length must be at least 0.1 cm")
        @Schema(description = "Optional, defaults to 10 cm")
        BigDecimal lengthCm,

        @DecimalMin(value = "0.1", message = "Width must be at least 0.1 cm")
        @Schema(description = "Optional, defaults to 10 cm")
        BigDecimal widthCm,

        @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
        @Schema(description = "Optional, defaults to 10 cm")
        BigDecimal heightCm,

        @NotNull(message = "Actual weight is mandatory")
        @DecimalMin(value = ValidationPatterns.WEIGHT_MIN, message = ValidationPatterns.WEIGHT_MIN_MESSAGE)
        @DecimalMax(value = ValidationPatterns.WEIGHT_MAX, message = ValidationPatterns.WEIGHT_MAX_MESSAGE)
        BigDecimal actualWeightKg,

        OrderSpeed speed,

        @Size(max = 512, message = "Must be at most 512 characters")
        @Pattern(regexp = ValidationPatterns.FREE_TEXT, message = ValidationPatterns.FREE_TEXT_MESSAGE)
        String itemDescription,

        @DecimalMin(value = "0.0", message = "Declared value must be non-negative")
        BigDecimal declaredValue,

        @DecimalMin(value = "0.0", message = "COD amount must be non-negative")
        BigDecimal codAmount,

        @NotNull(message = "Prohibited items declaration is mandatory")
        @AssertTrue(message = "You must declare that the parcel does not contain any prohibited items")
        Boolean prohibitedItemsDeclared
) {

    public static final String DEFAULT_DIMENSION_CM = "10";

    /** Maps to the customer request used by the shared creation path, applying the dimension and speed defaults. */
    public CreateOrderRequest toCreateOrderRequest() {
        BigDecimal defaultDimension = new BigDecimal(DEFAULT_DIMENSION_CM);
        return CreateOrderRequest.builder()
                .merchantId(merchantId)
                .pickupAddressLine1(pickupAddressLine1)
                .pickupAddressLine2(pickupAddressLine2)
                .pickupCity(pickupCity)
                .pickupDistrict(pickupDistrict)
                .pickupPostalCode(pickupPostalCode)
                .pickupContactName(pickupContactName)
                .pickupContactPhone(pickupContactPhone)
                .deliveryAddressLine1(deliveryAddressLine1)
                .deliveryAddressLine2(deliveryAddressLine2)
                .deliveryCity(deliveryCity)
                .deliveryDistrict(deliveryDistrict)
                .deliveryPostalCode(deliveryPostalCode)
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .recipientEmail(recipientEmail)
                .lengthCm(lengthCm != null ? lengthCm : defaultDimension)
                .widthCm(widthCm != null ? widthCm : defaultDimension)
                .heightCm(heightCm != null ? heightCm : defaultDimension)
                .actualWeightKg(actualWeightKg)
                .speed(speed != null ? speed : OrderSpeed.STANDARD)
                .itemDescription(itemDescription)
                .declaredValue(declaredValue != null ? declaredValue : BigDecimal.ZERO)
                .codAmount(codAmount != null ? codAmount : BigDecimal.ZERO)
                .prohibitedItemsDeclared(prohibitedItemsDeclared)
                .build();
    }
}
