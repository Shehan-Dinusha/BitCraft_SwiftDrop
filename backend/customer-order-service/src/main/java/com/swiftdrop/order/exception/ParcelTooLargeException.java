package com.swiftdrop.order.exception;

import com.swiftdrop.contracts.parcel.ParcelLimits;

/** No vehicle carries a parcel this big, so it could never be assigned to a courier. */
public class ParcelTooLargeException extends RuntimeException {
    public ParcelTooLargeException() {
        super(ParcelLimits.MAX_VOLUME_MESSAGE);
    }
}
