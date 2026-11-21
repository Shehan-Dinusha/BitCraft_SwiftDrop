package com.swiftdrop.merchant.domain.entity;

/** The business's own estimate of parcels a month, given on its application. */
public enum MonthlyParcelVolume {
    UP_TO_50,
    FROM_51_TO_200,
    FROM_201_TO_1000,
    OVER_1000
}
