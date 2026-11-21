package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerAddress;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A saved pickup or drop-off address")
public record SavedAddressResponse(
        @Schema(description = "Address ID") UUID addressId,
        @Schema(description = "The customer's name for it") String label,
        @Schema(description = "Person at this address") String contactName,
        @Schema(description = "Contact phone, +94 and 9 digits") String contactPhone,
        @Schema(description = "Optional second phone") String alternatePhone,
        @Schema(description = "House or building number") String house,
        @Schema(description = "Street or road") String street,
        @Schema(description = "Area or locality") String area,
        @Schema(description = "Landmark") String landmark,
        @Schema(description = "City or town") String city,
        @Schema(description = "District") SriLankanDistrict district,
        @Schema(description = "5-digit postal code") String postalCode,
        @Schema(description = "Last change") Instant updatedAt
) {
    public static SavedAddressResponse from(CustomerAddress a) {
        return new SavedAddressResponse(a.getAddressId(), a.getLabel(), a.getContactName(), a.getContactPhone(),
                a.getAlternatePhone(), a.getHouse(), a.getStreet(), a.getArea(), a.getLandmark(), a.getCity(),
                a.getDistrict(), a.getPostalCode(), a.getUpdatedAt());
    }
}
