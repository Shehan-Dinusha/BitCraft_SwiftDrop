package com.swiftdrop.order.service.promise;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * The parts of the courier working day the delivery promise needs, as the SYSTEM_ADMIN set them in the dispatch
 * service. {@link #STANDARD} is the agreed plan, used whenever the dispatch calendar cannot answer.
 *
 * @param workingDays   the weekdays couriers work
 * @param sameDayCutoff Same day parcels are booked before this
 * @param pickupCutoff  a parcel booked before this is picked up the same working day
 */
public record WorkingRules(Set<DayOfWeek> workingDays, LocalTime sameDayCutoff, LocalTime pickupCutoff) {

    private static final Set<DayOfWeek> STANDARD_DAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);

    public static final WorkingRules STANDARD =
            new WorkingRules(STANDARD_DAYS, LocalTime.of(11, 0), LocalTime.of(15, 0));

    public WorkingRules {
        workingDays = workingDays == null || workingDays.isEmpty()
                ? EnumSet.copyOf(STANDARD_DAYS) : EnumSet.copyOf(workingDays);
    }
}
