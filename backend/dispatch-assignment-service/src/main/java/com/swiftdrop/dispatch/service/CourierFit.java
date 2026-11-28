package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.CourierBlock;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a courier can take a parcel: on shift, the parcel fits the vehicle, and there is room left for it
 * (parcels, weight and volume). A courier on their lunch break keeps their parcels and can be given more; the
 * suggestions list them after the couriers who are working.
 *
 * <p>The day decides two more things: a parcel waiting at a hub goes only to a courier who has not yet left on
 * their run, and pickups are given out only until the pickup cut-off ({@link WorkHours#pickupCutoff()}).</p> The suggestions and the assign and reassign checks all use this,
 * so what a dispatcher is offered is what the server accepts.
 *
 * <p>An order from before sizes were recorded has no size, so only the shift and parcel count apply to it.</p>
 */
public final class CourierFit {

    private CourierFit() {
    }

    /** As {@link #block(CourierShift, CourierLoad, DispatchAssignment, boolean)}, with pickups still open. */
    public static Optional<CourierBlock> block(CourierShift courier, CourierLoad load, DispatchAssignment parcel) {
        return block(courier, load, parcel, false);
    }

    /**
     * The first reason the courier cannot take the parcel, or empty when they can.
     *
     * @param pickupsClosed whether it is past the pickup cut-off ({@link WorkingDay#pickupsClosed()})
     */
    public static Optional<CourierBlock> block(CourierShift courier, CourierLoad load, DispatchAssignment parcel,
                                               boolean pickupsClosed) {
        if (courier.getShiftStatus() == ShiftStatus.OFFLINE) {
            return Optional.of(CourierBlock.OFF_SHIFT);
        }
        if (parcel.startsAtHub() && courier.isOnRun()) {
            return Optional.of(CourierBlock.ON_RUN);
        }
        if (!parcel.startsAtHub() && pickupsClosed) {
            return Optional.of(CourierBlock.PICKUPS_CLOSED);
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
        if (weight != null && exceeds(load.weightKg().add(weight), vehicle.maxWeightKg())) {
            return Optional.of(CourierBlock.WEIGHT_LIMIT);
        }
        if (volume != null && exceeds(load.volumeLitres().add(volume), vehicle.maxVolumeLitres())) {
            return Optional.of(CourierBlock.VOLUME_LIMIT);
        }
        return Optional.empty();
    }

    /**
     * The parcels the courier can take together, in the order given: each one counts towards the limits of
     * the next, as if it were already on board.
     */
    public static List<DispatchAssignment> fitting(CourierShift courier, CourierLoad load, List<DispatchAssignment> parcels,
                                                   boolean pickupsClosed) {
        List<DispatchAssignment> taken = new ArrayList<>();
        CourierLoad held = load;
        for (DispatchAssignment parcel : parcels) {
            boolean room = courier.getActiveParcelsCount() + taken.size() < courier.getMaxCapacity();
            if (room && block(courier, held, parcel, pickupsClosed).isEmpty()) {
                taken.add(parcel);
                held = held.plus(parcel);
            }
        }
        return taken;
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
            case ON_RUN -> name + " has left on their run. Give hub parcels to a courier still at the hub.";
            case PICKUPS_CLOSED -> "Pickups are closed for today. This one waits for the next working day's run.";
            case PARCEL_LIMIT -> name + " already carries " + courier.getMaxCapacity() + " parcels";
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
