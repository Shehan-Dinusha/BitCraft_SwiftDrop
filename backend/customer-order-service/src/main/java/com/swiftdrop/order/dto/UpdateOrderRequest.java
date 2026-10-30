package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer order edit request payload (permitted only in CREATED status)")
public class UpdateOrderRequest {

    @Schema(description = "Pickup address line 1", example = "No. 42, Galle Road")
    @Size(min = 5, max = 200, message = "Must be between 5 and 200 characters")
    @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
    private String pickupAddressLine1;

    @Schema(description = "Pickup address line 2", example = "Suite 4B")
    @Size(max = 200, message = "Must be at most 200 characters")
    @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
    private String pickupAddressLine2;

    @Schema(description = "Pickup city", example = "Colombo 03")
    @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
    @Pattern(regexp = ValidationPatterns.CITY, message = ValidationPatterns.CITY_MESSAGE)
    private String pickupCity;

    @Schema(description = "Pickup district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @Schema(description = "Pickup postal code", example = "00300")
    @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = ValidationPatterns.POSTAL_CODE_MESSAGE)
    private String pickupPostalCode;

    @Schema(description = "Pickup contact name", example = "Sunil Perera")
    @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
    @Pattern(regexp = ValidationPatterns.NAME, message = ValidationPatterns.NAME_MESSAGE)
    private String pickupContactName;

    @Schema(description = "Pickup contact phone", example = "+94771234567")
    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String pickupContactPhone;

    @Schema(description = "Delivery address line 1", example = "No. 18, Peradeniya Road")
    @Size(min = 5, max = 200, message = "Must be between 5 and 200 characters")
    @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
    private String deliveryAddressLine1;

    @Schema(description = "Delivery address line 2", example = "Hilltop Residence")
    @Size(max = 200, message = "Must be at most 200 characters")
    @Pattern(regexp = ValidationPatterns.ADDRESS, message = ValidationPatterns.ADDRESS_MESSAGE)
    private String deliveryAddressLine2;

    @Schema(description = "Delivery city", example = "Kandy")
    @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
    @Pattern(regexp = ValidationPatterns.CITY, message = ValidationPatterns.CITY_MESSAGE)
    private String deliveryCity;

    @Schema(description = "Delivery district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @Schema(description = "Delivery postal code", example = "20000")
    @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = ValidationPatterns.POSTAL_CODE_MESSAGE)
    private String deliveryPostalCode;

    @Schema(description = "Recipient name", example = "Anura Fernando")
    @Size(min = 2, max = 100, message = "Must be between 2 and 100 characters")
    @Pattern(regexp = ValidationPatterns.NAME, message = ValidationPatterns.NAME_MESSAGE)
    private String recipientName;

    @Schema(description = "Recipient phone", example = "+94719876543")
    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String recipientPhone;

    @Email(message = "Recipient email must be a valid email address")
    @Schema(description = "Recipient email address", example = "anura.f@gmail.com")
    @Size(max = 128, message = "Must be at most 128 characters")
    private String recipientEmail;

    @DecimalMin(value = "0.1", message = "Length must be at least 0.1 cm")
    @Schema(description = "Package length in cm", example = "25.0")
    private BigDecimal lengthCm;

    @DecimalMin(value = "0.1", message = "Width must be at least 0.1 cm")
    @Schema(description = "Package width in cm", example = "20.0")
    private BigDecimal widthCm;

    @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
    @Schema(description = "Package height in cm", example = "10.0")
    private BigDecimal heightCm;

    @DecimalMin(value = "0.01", message = "Weight must be at least 0.01 kg")
    @Schema(description = "Package actual weight in kg", example = "1.50")
    @DecimalMax(value = ValidationPatterns.WEIGHT_MAX, message = ValidationPatterns.WEIGHT_MAX_MESSAGE)
    private BigDecimal actualWeightKg;

    @Schema(description = "Delivery service speed level", example = "STANDARD")
    private OrderSpeed speed;

    @Schema(description = "Item description", example = "Handicrafts and tea canisters")
    @Size(max = 512, message = "Must be at most 512 characters")
    @Pattern(regexp = ValidationPatterns.FREE_TEXT, message = ValidationPatterns.FREE_TEXT_MESSAGE)
    private String itemDescription;

    @Schema(description = "Declared merchandise value in LKR", example = "5000.00")
    private BigDecimal declaredValue;

    @Schema(description = "COD amount in LKR", example = "0.00")
    private BigDecimal codAmount;
}
