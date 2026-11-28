package com.swiftdrop.dispatch.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * The courier working day as the SYSTEM_ADMIN set it, in Sri Lanka time. {@link #STANDARD} is the agreed plan:
 * Monday to Saturday, 8:00 to 17:00, check-in from 7:30 (after 9:00 is late), one lunch break of 45 minutes
 * started between 12:00 and 14:30, new pickups until 15:00 and Same day bookings until 11:00.
 *
 * @param workingDays   the weekdays couriers work; the others are closed every week
 * @param opensAt       the working day opens
 * @param closesAt      the working day closes; shifts cannot start from then on
 * @param shiftFrom     couriers may check in from this time, a little before opening
 * @param lateAfter     a shift started after this shows as a late start
 * @param breakFrom     the lunch break can start from
 * @param breakUntil    the lunch break can start until
 * @param breakMinutes  the lunch break length
 * @param pickupCutoff  no new pickups are given out from then on
 * @param sameDayCutoff Same day parcels are booked before this (read by the order service)
 */
public record WorkHours(
        Set<DayOfWeek> workingDays,
        LocalTime opensAt,
        LocalTime closesAt,
        LocalTime shiftFrom,
        LocalTime lateAfter,
        LocalTime breakFrom,
        LocalTime breakUntil,
        int breakMinutes,
        LocalTime pickupCutoff,
        LocalTime sameDayCutoff) {

    public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    /**
     * The night clean-up closes forgotten shifts every hour from 20:00 to 6:00
     * ({@code CourierShiftService.closeForgottenShifts}), so the working day must fit between these.
     */
    public static final LocalTime EARLIEST_SHIFT = LocalTime.of(7, 0);
    public static final LocalTime LATEST_CLOSE = LocalTime.of(20, 0);

    public static final WorkHours STANDARD = new WorkHours(
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            LocalTime.of(7, 30),
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            LocalTime.of(14, 30),
            45,
            LocalTime.of(15, 0),
            LocalTime.of(11, 0));

    public WorkHours {
        workingDays = workingDays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(workingDays);
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDays.contains(day);
    }

    /** When a break started at {@code started} should end. */
    public Instant breakEndsAt(Instant started) {
        return started == null ? null : started.plus(Duration.ofMinutes(breakMinutes));
    }

    /** Whether a shift started at {@code started} started after {@link #lateAfter}. */
    public boolean startedLate(Instant started) {
        return started != null && started.atZone(ZONE).toLocalTime().isAfter(lateAfter);
    }

    /**
     * Checks that the hours make a workable day. Throws {@link IllegalArgumentException} (400) with a message
     * the admin can act on.
     */
    public void validate() {
        if (workingDays.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one working day.");
        }
        if (!opensAt.isBefore(closesAt)) {
            throw new IllegalArgumentException("The day must close after it opens.");
        }
        if (closesAt.isAfter(LATEST_CLOSE)) {
            throw new IllegalArgumentException("The day must close by 20:00: open shifts are closed from then on.");
        }
        if (shiftFrom.isBefore(EARLIEST_SHIFT) || shiftFrom.isAfter(opensAt)) {
            throw new IllegalArgumentException("Check-in opens between 7:00 and the opening time.");
        }
        if (lateAfter.isBefore(opensAt) || !lateAfter.isBefore(closesAt)) {
            throw new IllegalArgumentException("The late-start time must be within working hours.");
        }
        if (!breakFrom.isBefore(breakUntil)) {
            throw new IllegalArgumentException("The lunch window must end after it starts.");
        }
        if (breakFrom.isBefore(opensAt) || !breakUntil.isBefore(closesAt)) {
            throw new IllegalArgumentException("The lunch window must be within working hours.");
        }
        if (breakMinutes < 10 || breakMinutes > 120) {
            throw new IllegalArgumentException("The lunch break is 10 to 120 minutes.");
        }
        if (!pickupCutoff.isAfter(opensAt) || pickupCutoff.isAfter(closesAt)) {
            throw new IllegalArgumentException("The pickup cut-off must be within working hours.");
        }
        if (!sameDayCutoff.isAfter(opensAt) || sameDayCutoff.isAfter(closesAt)) {
            throw new IllegalArgumentException("The Same day cut-off must be within working hours.");
        }
    }
}
