package com.swiftdrop.contracts.parcel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The largest parcel SwiftDrop accepts: what fits its biggest vehicle (a lorry). A bigger one could be
 * booked but never assigned to a courier, so it is rejected at booking.
 */
public final class ParcelLimits {

    /** Longest side, in cm (a string, so validation annotations can use it). */
    public static final String MAX_SIDE_CM = "500";
    public static final String MAX_SIDE_MESSAGE = "Must not exceed 500 cm";
    public static final int MAX_VOLUME_LITRES = 15_000;
    public static final String MAX_VOLUME_MESSAGE = "The parcel is too big: length x width x height must not exceed 15,000 litres";

    private static final BigDecimal CM3_PER_LITRE = BigDecimal.valueOf(1000);

    private ParcelLimits() {
    }

    /** Volume in litres, or {@code null} when a side is unknown. */
    public static BigDecimal volumeLitres(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        if (lengthCm == null || widthCm == null || heightCm == null) {
            return null;
        }
        return lengthCm.multiply(widthCm).multiply(heightCm).divide(CM3_PER_LITRE, 2, RoundingMode.HALF_UP);
    }

    /** False while a side is unknown. */
    public static boolean exceedsMaxVolume(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        BigDecimal volume = volumeLitres(lengthCm, widthCm, heightCm);
        return volume != null && volume.compareTo(BigDecimal.valueOf(MAX_VOLUME_LITRES)) > 0;
    }
}
