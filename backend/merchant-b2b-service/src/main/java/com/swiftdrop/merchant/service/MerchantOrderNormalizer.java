package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.dto.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MerchantOrderNormalizer {
    private final Validator validator;

    public Map<String, Object> normalize(UUID merchantId, B2bOrderRequest request) {
        if (request == null) throw new IllegalArgumentException("Order is required");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(Collectors.joining("; ")));
        B2bParcel parcel = request.parcel();
        if ((parcel.actualWeightKg() == null) == (parcel.weightGrams() == null)) {
            throw new IllegalArgumentException("Supply exactly one weight unit");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", merchantId);
        body.put("externalRef", request.externalRef());
        address(body, "pickup", request.pickup());
        address(body, "delivery", request.delivery());
        body.put("pickupContactName", request.pickup().contactName().trim());
        body.put("pickupContactPhone", phone(request.pickup().contactPhone()));
        body.put("recipientName", request.delivery().contactName().trim());
        body.put("recipientPhone", phone(request.delivery().contactPhone()));
        body.put("actualWeightKg", parcel.actualWeightKg() != null ? parcel.actualWeightKg() : parcel.weightGrams().movePointLeft(3));
        body.put("prohibitedItemsDeclared", parcel.prohibitedItemsDeclared());
        body.put("itemDescription", parcel.itemDescription());
        body.put("declaredValue", parcel.declaredValue() != null ? parcel.declaredValue() : BigDecimal.ZERO);
        body.put("codAmount", parcel.codAmount() != null ? parcel.codAmount() : BigDecimal.ZERO);
        try {
            body.put("speed", request.speed() == null || request.speed().isBlank() ? OrderSpeed.STANDARD
                    : OrderSpeed.valueOf(request.speed().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Speed must be STANDARD or EXPRESS");
        }
        return body;
    }

    private void address(Map<String, Object> body, String prefix, B2bAddress address) {
        body.put(prefix + "AddressLine1", address.addressLine1().trim());
        body.put(prefix + "City", address.city().trim());
        body.put(prefix + "District", district(address.district()));
        body.put(prefix + "PostalCode", address.postalCode());
    }

    public SriLankanDistrict district(String value) {
        try { return SriLankanDistrict.valueOf(value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_")); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid Sri Lankan district"); }
    }

    public String phone(String value) {
        String number = value.trim().replaceAll("[\\s()\\-]", "");
        if (number.startsWith("0094")) number = "+" + number.substring(2);
        else if (number.matches("94\\d{9}")) number = "+" + number;
        else if (number.matches("0\\d{9}")) number = "+94" + number.substring(1);
        if (!number.matches("\\+94\\d{9}")) throw new IllegalArgumentException("Invalid Sri Lankan phone number");
        return number;
    }
}
