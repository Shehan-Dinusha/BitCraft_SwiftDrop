package com.swiftdrop.analytics.ai.tools;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderLookupTool {

    public record OrderSnapshot(
            String trackingNumber,
            PublicOrderStatus status,
            SriLankanDistrict originDistrict,
            SriLankanDistrict destinationDistrict,
            String courierName
    ) {}

    public Optional<OrderSnapshot> lookup(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Optional.empty();
        }

        // Return contextual snapshot based on tracking number format or defaults
        SriLankanDistrict origin = SriLankanDistrict.COLOMBO;
        SriLankanDistrict dest = SriLankanDistrict.KANDY;
        if (trackingNumber.toUpperCase().contains("GAL")) {
            dest = SriLankanDistrict.GALLE;
        } else if (trackingNumber.toUpperCase().contains("JAF")) {
            dest = SriLankanDistrict.JAFFNA;
        }

        return Optional.of(new OrderSnapshot(
                trackingNumber,
                PublicOrderStatus.IN_TRANSIT,
                origin,
                dest,
                "Courier-Active"
        ));
    }
}
