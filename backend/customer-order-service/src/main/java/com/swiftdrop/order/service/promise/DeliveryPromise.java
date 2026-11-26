package com.swiftdrop.order.service.promise;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.exception.SameDayNotAvailableException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * The day a parcel is promised, in Sri Lanka time. Couriers work Monday to Saturday; Sundays and the listed
 * holidays are closed.
 *
 * <ul>
 *   <li>Same day: within one district only, booked by 11:00 on a working day; one courier takes it straight
 *       to the recipient, today.</li>
 *   <li>Otherwise the parcel is picked up today when booked before 15:00 on a working day, else on the next
 *       working day, and goes through the hub: Express the next working day after pickup, Standard two working
 *       days after (within one district) or three (to another district).</li>
 * </ul>
 */
@Component
public class DeliveryPromise {

    public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    public static final LocalTime SAME_DAY_CUTOFF = LocalTime.of(11, 0);
    public static final LocalTime PICKUP_CUTOFF = LocalTime.of(15, 0);

    private final Clock clock;
    private final ClosedDaysClient closedDays;

    public DeliveryPromise(Clock clock, ClosedDaysClient closedDays) {
        this.clock = clock;
        this.closedDays = closedDays;
    }

    /**
     * @throws SameDayNotAvailableException when Same day is asked for between districts, after 11:00 or on a
     *                                      closed day
     */
    public LocalDate deliverBy(OrderSpeed speed, SriLankanDistrict from, SriLankanDistrict to) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZONE));
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        // Enough days ahead for a Standard parcel across a run of holidays.
        Set<LocalDate> holidays = closedDays.between(today, today.plusDays(21));
        boolean sameDistrict = from != null && from == to;

        if (speed == OrderSpeed.SAME_DAY) {
            if (!sameDistrict) {
                throw new SameDayNotAvailableException("Same day delivery is only within one district.");
            }
            if (!working(today, holidays) || !time.isBefore(SAME_DAY_CUTOFF)) {
                throw new SameDayNotAvailableException(
                        "Same day delivery is booked by 11:00 on a working day. Choose Express for the next working day.");
            }
            return today;
        }

        LocalDate pickup = working(today, holidays) && time.isBefore(PICKUP_CUTOFF) ? today : next(today, holidays);
        int days = speed == OrderSpeed.EXPRESS ? 1 : sameDistrict ? 2 : 3;
        LocalDate day = pickup;
        for (int i = 0; i < days; i++) {
            day = next(day, holidays);
        }
        return day;
    }

    private static boolean working(LocalDate day, Set<LocalDate> holidays) {
        return day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(day);
    }

    private static LocalDate next(LocalDate day, Set<LocalDate> holidays) {
        LocalDate next = day.plusDays(1);
        while (!working(next, holidays)) {
            next = next.plusDays(1);
        }
        return next;
    }
}
