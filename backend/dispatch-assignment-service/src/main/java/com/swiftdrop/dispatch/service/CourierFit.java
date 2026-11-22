package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.CourierBlock;
import com.swiftdrop.dispatch.domain.enums.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Whether a courier can take a parcel: on shift and free, the parcel fits the vehicle, and there is room
 * left for it (parcels, weight and volume). The suggestions and the assign and reassign checks all use this,
 * so what a dispatcher is offered is what the server accepts.
 *
 * <p>An order from before sizes were recorded has no size, so only the shift and parcel count apply to it.</p>
 */
public final class CourierFit {

    private CourierFit() {
    }

    /** The first reason the courier cannot take the parcel, or empty when they can. */
    public static Optional<CourierBlock> block(CourierShift courier, CourierLoad load, DispatchAssignment parcel) {
        switch (courier.getShiftStatus()) {
            case OFFLINE -> {
                return Optional.of(CourierBlock.OFF_SHIFT);
            }
            case ON_BREAK -> {
                return Optional.of(CourierBlock.ON_BREAK);
            }
            default -> {
                // AVAILABLE and BUSY are checked below, after the vehicle: "too long" is the better reason.
            }
        }
        VehicleType vehicle = courier.getVehicleType();
        BigDecimal weight = parcel.getWeightKg();
        BigDecimal volume = parcel.getVolumeLitres();
        if (exceeds(parcel.getLongestSideCm(), vehicle.maxSideCm())) {
            return Optional.of(CourierBlock.TOO_LONG);
        }
        if (exceeds(weight, vehicle.maxWeightKg())) {
            return Optional.of(CourierBlock.TOO_HEAVY);
        }
        if (exceeds(volume, vehicle.maxVolumeLitres())) {
            return Optional.of(CourierBlock.TOO_BIG);
        }
        if (courier.getActiveParcelsCount() >= courier.getMaxCapacity()) {
            return Optional.of(CourierBlock.PARCEL_LIMIT);
        }
        if (!courier.canAcceptParcel()) {
            return Optional.of(CourierBlock.BUSY);
        }
        if (weight != null && exceeds(load.weightKg().add(weight), vehicle.maxWeightKg())) {
            return Optional.of(CourierBlock.WEIGHT_LIMIT);
        }
        if (volume != null && exceeds(load.volumeLitres().add(volume), vehicle.maxVolumeLitres())) {
            return Optional.of(CourierBlock.VOLUME_LIMIT);
        }
        return Optional.empty();
    }

    /** How full the courier is, from 0 to 1: the fullest of parcels, weight and volume. */
    public static double fullness(CourierShift courier, CourierLoad load) {
        VehicleType vehicle = courier.getVehicleType();
        double parcels = courier.getMaxCapacity() > 0
                ? (double) courier.getActiveParcelsCount() / courier.getMaxCapacity() : 1;
        return Math.max(parcels, Math.max(
                load.weightKg().doubleValue() / vehicle.maxWeightKg(),
                load.volumeLitres().doubleValue() / vehicle.maxVolumeLitres()));
    }

    /** The message a dispatcher gets when an assignment is refused. */
    public static String message(CourierBlock block, CourierShift courier, CourierLoad load, DispatchAssignment parcel) {
        String name = "Courier " + courier.getCourierName();
        VehicleType vehicle = courier.getVehicleType();
        String takes = "; a " + vehicle.label() + " takes up to ";
        return switch (block) {
            case OFF_SHIFT -> name + " is currently OFFLINE";
            case ON_BREAK, BUSY, PARCEL_LIMIT -> name + " is not available or has reached capacity";
            case TOO_LONG -> "The parcel is " + plain(parcel.getLongestSideCm()) + " cm long" + takes
                    + vehicle.maxSideCm() + " cm";
            case TOO_HEAVY -> "The parcel weighs " + plain(parcel.getWeightKg()) + " kg" + takes
                    + vehicle.maxWeightKg() + " kg";
            case TOO_BIG -> "The parcel is " + plain(parcel.getVolumeLitres()) + " litres" + takes
                    + vehicle.maxVolumeLitres() + " litres";
            case WEIGHT_LIMIT -> name + " already carries " + plain(load.weightKg()) + " of "
                    + vehicle.maxWeightKg() + " kg, so this " + plain(parcel.getWeightKg()) + " kg parcel does not fit";
            case VOLUME_LIMIT -> name + " already carries " + plain(load.volumeLitres()) + " of "
                    + vehicle.maxVolumeLitres() + " litres, so this " + plain(parcel.getVolumeLitres())
                    + " litre parcel does not fit";
        };
    }

    private static boolean exceeds(BigDecimal value, int limit) {
        return value != null && value.compareTo(BigDecimal.valueOf(limit)) > 0;
    }

    private static String plain(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
