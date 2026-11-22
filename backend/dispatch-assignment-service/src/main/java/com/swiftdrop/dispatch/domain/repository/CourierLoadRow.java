package com.swiftdrop.dispatch.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Weight and volume of the parcels a courier holds. A sum is null when no parcel has a recorded size. */
public interface CourierLoadRow {
    UUID getCourierId();

    BigDecimal getWeightKg();

    BigDecimal getVolumeLitres();
}
