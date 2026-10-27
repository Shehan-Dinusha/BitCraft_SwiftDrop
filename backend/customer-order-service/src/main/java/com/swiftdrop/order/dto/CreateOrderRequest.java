package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer order creation request payload (FR-C1)")
public class CreateOrderRequest {

    @Schema(description = "Customer UUID (optional if provided via X-User-Id header)")
    private UUID customerId;

    @Schema(description = "Merchant UUID (optional for B2B)")
    private UUID merchantId;

    // Pickup Details
    @NotBlank(message = "Pickup address line 1 is mandatory")
    @Schema(description = "Pickup address line 1", example = "No. 42, Galle Road")
    private String pickupAddressLine1;

    @Schema(description = "Pickup address line 2", example = "Suite 4B")
    private String pickupAddressLine2;

    @NotBlank(message = "Pickup city is mandatory")
    @Schema(description = "Pickup city", example = "Colombo 03")
    private String pickupCity;

    @NotNull(message = "Pickup district is mandatory")
    @Schema(description = "Pickup district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @NotBlank(message = "Pickup postal code is mandatory")
    @Schema(description = "Pickup postal code", example = "00300")
    private String pickupPostalCode;

    @NotBlank(message = "Pickup contact name is mandatory")
    @Schema(description = "Pickup contact person name", example = "Sunil Perera")
    private String pickupContactName;

    @NotBlank(message = "Pickup contact phone is mandatory")
    @Schema(description = "Pickup contact telephone", example = "+94771234567")
    private String pickupContactPhone;

    // Delivery / Recipient Details
    @NotBlank(message = "Delivery address line 1 is mandatory")
    @Schema(description = "Delivery address line 1", example = "No. 18, Peradeniya Road")
    private String deliveryAddressLine1;

    @Schema(description = "Delivery address line 2", example = "Hilltop Residence")
    private String deliveryAddressLine2;

    @NotBlank(message = "Delivery city is mandatory")
    @Schema(description = "Delivery city", example = "Kandy")
    private String deliveryCity;

    @NotNull(message = "Delivery district is mandatory")
    @Schema(description = "Delivery district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @NotBlank(message = "Delivery postal code is mandatory")
    @Schema(description = "Delivery postal code", example = "20000")
    private String deliveryPostalCode;

    @NotBlank(message = "Recipient name is mandatory")
    @Schema(description = "Recipient full name", example = "Anura Fernando")
    private String recipientName;

    @NotBlank(message = "Recipient phone is mandatory")
    @Schema(description = "Recipient phone number", example = "+94719876543")
    private String recipientPhone;

    @NotBlank(message = "Recipient email is mandatory")
    @Email(message = "Recipient email must be a valid email address")
    @Schema(description = "Recipient email address for tracking notifications", example = "anura.f@gmail.com")
    private String recipientEmail;

    // Physical Dimensions & Weight
    @NotNull(message = "Length is mandatory")
    @DecimalMin(value = "0.1", message = "Length must be at least 0.1 cm")
    @Schema(description = "Package length in cm", example = "25.0")
    private BigDecimal lengthCm;

    @NotNull(message = "Width is mandatory")
    @DecimalMin(value = "0.1", message = "Width must be at least 0.1 cm")
    @Schema(description = "Package width in cm", example = "20.0")
    private BigDecimal widthCm;

    @NotNull(message = "Height is mandatory")
    @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
    @Schema(description = "Package height in cm", example = "10.0")
    private BigDecimal heightCm;

    @NotNull(message = "Actual weight is mandatory")
    @DecimalMin(value = "0.01", message = "Weight must be at least 0.01 kg")
    @Schema(description = "Package actual weight in kg", example = "1.50")
    private BigDecimal actualWeightKg;

    @Schema(description = "Delivery service speed level", example = "STANDARD")
    @Builder.Default
    private OrderSpeed speed = OrderSpeed.STANDARD;

    @Schema(description = "Description of parcel contents", example = "Handicrafts and tea canisters")
    private String itemDescription;

    @DecimalMin(value = "0.0", message = "Declared value must be non-negative")
    @Schema(description = "Declared merchandise value in LKR", example = "5000.00")
    @Builder.Default
    private BigDecimal declaredValue = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "COD amount must be non-negative")
    @Schema(description = "Cash on Delivery collection amount in LKR", example = "0.00")
    @Builder.Default
    private BigDecimal codAmount = BigDecimal.ZERO;

    // Mandatory Prohibited-Items Declaration
    @NotNull(message = "Prohibited items declaration is mandatory")
    @AssertTrue(message = "You must declare that the parcel does not contain any prohibited items")
    @Schema(description = "Mandatory declaration that parcel contains no illegal or prohibited items", example = "true")
    private Boolean prohibitedItemsDeclared;

    @Schema(description = "Optional locked pricing rule ID from prior quote", example = "PRICING-2026-V1")
    private String pricingRuleVersionId;
}
