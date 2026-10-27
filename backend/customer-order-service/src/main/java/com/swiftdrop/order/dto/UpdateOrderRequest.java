package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
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
    private String pickupAddressLine1;

    @Schema(description = "Pickup address line 2", example = "Suite 4B")
    private String pickupAddressLine2;

    @Schema(description = "Pickup city", example = "Colombo 03")
    private String pickupCity;

    @Schema(description = "Pickup district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @Schema(description = "Pickup postal code", example = "00300")
    private String pickupPostalCode;

    @Schema(description = "Pickup contact name", example = "Sunil Perera")
    private String pickupContactName;

    @Schema(description = "Pickup contact phone", example = "+94771234567")
    private String pickupContactPhone;

    @Schema(description = "Delivery address line 1", example = "No. 18, Peradeniya Road")
    private String deliveryAddressLine1;

    @Schema(description = "Delivery address line 2", example = "Hilltop Residence")
    private String deliveryAddressLine2;

    @Schema(description = "Delivery city", example = "Kandy")
    private String deliveryCity;

    @Schema(description = "Delivery district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @Schema(description = "Delivery postal code", example = "20000")
    private String deliveryPostalCode;

    @Schema(description = "Recipient name", example = "Anura Fernando")
    private String recipientName;

    @Schema(description = "Recipient phone", example = "+94719876543")
    private String recipientPhone;

    @Email(message = "Recipient email must be a valid email address")
    @Schema(description = "Recipient email address", example = "anura.f@gmail.com")
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
    private BigDecimal actualWeightKg;

    @Schema(description = "Delivery service speed level", example = "STANDARD")
    private OrderSpeed speed;

    @Schema(description = "Item description", example = "Handicrafts and tea canisters")
    private String itemDescription;

    @Schema(description = "Declared merchandise value in LKR", example = "5000.00")
    private BigDecimal declaredValue;

    @Schema(description = "COD amount in LKR", example = "0.00")
    private BigDecimal codAmount;
}
