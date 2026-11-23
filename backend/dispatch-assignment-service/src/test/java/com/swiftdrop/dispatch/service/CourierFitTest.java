package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.parcel.ParcelLimits;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.CourierBlock;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CourierFitTest {

    @Test
    @DisplayName("A parcel exactly at the vehicle's limits fits; just over any of them does not")
    void vehicleLimits() {
        CourierShift motorcycle = courier(VehicleType.MOTORCYCLE, 0);
        assertThat(CourierFit.block(motorcycle, CourierLoad.NONE, parcel("50", "50", "40", "25"))).isEmpty(); // 100 l
        assertThat(CourierFit.block(motorcycle, CourierLoad.NONE, parcel("10", "50.1", "10", "1"))).contains(CourierBlock.TOO_LONG);
        assertThat(CourierFit.block(motorcycle, CourierLoad.NONE, parcel("10", "10", "10", "25.01"))).contains(CourierBlock.TOO_HEAVY);
        assertThat(CourierFit.block(motorcycle, CourierLoad.NONE, parcel("50", "50", "41", "1"))).contains(CourierBlock.TOO_BIG);
        assertThat(CourierFit.block(courier(VehicleType.VAN, 0), CourierLoad.NONE, parcel("150", "50", "50", "45"))).isEmpty();
    }

    @Test
    @DisplayName("Room left counts: what the courier already holds is added to the parcel")
    void roomLeft() {
        CourierShift motorcycle = courier(VehicleType.MOTORCYCLE, 1);
        assertThat(CourierFit.block(motorcycle, load("20", "10"), parcel("10", "10", "10", "5"))).isEmpty();
        assertThat(CourierFit.block(motorcycle, load("20", "10"), parcel("10", "10", "10", "5.5"))).contains(CourierBlock.WEIGHT_LIMIT);
        assertThat(CourierFit.block(motorcycle, load("1", "99.5"), parcel("10", "10", "10", "1"))).contains(CourierBlock.VOLUME_LIMIT);
        assertThat(CourierFit.block(courier(VehicleType.MOTORCYCLE, 5), load("1", "1"), parcel("10", "10", "10", "1")))
                .contains(CourierBlock.PARCEL_LIMIT);
    }

    @Test
    @DisplayName("An off-shift courier cannot take anything; one on their lunch break can, with room left")
    void shiftStatus() {
        CourierShift courier = courier(VehicleType.LORRY, 0);
        courier.setShiftStatus(ShiftStatus.OFFLINE);
        assertThat(CourierFit.block(courier, CourierLoad.NONE, parcel("10", "10", "10", "1"))).contains(CourierBlock.OFF_SHIFT);

        courier.setShiftStatus(ShiftStatus.ON_BREAK);
        assertThat(CourierFit.block(courier, CourierLoad.NONE, parcel("10", "10", "10", "1"))).isEmpty();
        CourierShift full = courier(VehicleType.MOTORCYCLE, 5);
        full.setShiftStatus(ShiftStatus.ON_BREAK);
        assertThat(CourierFit.block(full, CourierLoad.NONE, parcel("10", "10", "10", "1"))).contains(CourierBlock.PARCEL_LIMIT);
    }

    @Test
    @DisplayName("An order without a recorded size is limited by the parcel count only")
    void unknownSize() {
        DispatchAssignment unsized = new DispatchAssignment();
        assertThat(CourierFit.block(courier(VehicleType.MOTORCYCLE, 4), load("25", "100"), unsized)).isEmpty();
        assertThat(CourierFit.block(courier(VehicleType.MOTORCYCLE, 5), CourierLoad.NONE, unsized)).contains(CourierBlock.PARCEL_LIMIT);
    }

    @Test
    @DisplayName("Whatever can be booked fits an empty lorry")
    void everyBookableParcelFitsALorry() {
        CourierShift lorry = courier(VehicleType.LORRY, 0);
        assertThat(ParcelLimits.exceedsMaxVolume(new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("150"))).isFalse();
        assertThat(CourierFit.block(lorry, CourierLoad.NONE, parcel("500", "200", "150", "50"))).isEmpty();
        assertThat(ParcelLimits.exceedsMaxVolume(new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("151"))).isTrue();
    }

    @Test
    @DisplayName("The refusal tells the dispatcher what does not fit")
    void messages() {
        CourierShift motorcycle = courier(VehicleType.MOTORCYCLE, 1);
        assertThat(CourierFit.message(CourierBlock.TOO_LONG, motorcycle, CourierLoad.NONE, parcel("120", "10", "10", "1")))
                .isEqualTo("The parcel is 120 cm long; a motorcycle takes up to 50 cm");
        assertThat(CourierFit.message(CourierBlock.WEIGHT_LIMIT, motorcycle, load("20.5", "10"), parcel("10", "10", "10", "5")))
                .isEqualTo("Courier Nimal already carries 20.5 of 25 kg, so this 5 kg parcel does not fit");
    }

    @Test
    @DisplayName("Fullness is the fullest of parcels, weight and volume")
    void fullness() {
        CourierShift motorcycle = courier(VehicleType.MOTORCYCLE, 1);
        assertThat(CourierFit.fullness(motorcycle, CourierLoad.NONE)).isCloseTo(0.2, within(1e-9));
        assertThat(CourierFit.fullness(motorcycle, load("20", "10"))).isCloseTo(0.8, within(1e-9));
        assertThat(CourierFit.fullness(motorcycle, load("1", "90"))).isCloseTo(0.9, within(1e-9));
    }

    private static CourierShift courier(VehicleType vehicle, int active) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(UUID.randomUUID());
        courier.setCourierName("Nimal");
        courier.setVehicleType(vehicle);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        courier.setActiveParcelsCount(active);
        courier.setMaxCapacity(vehicle.defaultCapacity());
        return courier;
    }

    private static CourierLoad load(String weight, String volume) {
        return new CourierLoad(new BigDecimal(weight), new BigDecimal(volume));
    }

    private static DispatchAssignment parcel(String length, String width, String height, String weight) {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setParcel(new BigDecimal(length), new BigDecimal(width), new BigDecimal(height), new BigDecimal(weight));
        return assignment;
    }
}
