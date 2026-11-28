package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.WorkingHoursSetting;
import com.swiftdrop.dispatch.domain.repository.ClosedDayRepository;
import com.swiftdrop.dispatch.domain.repository.WorkingHoursRepository;
import com.swiftdrop.dispatch.dto.WorkdayResponse;
import com.swiftdrop.dispatch.exception.ShiftRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The courier working day, in Sri Lanka time. The SYSTEM_ADMIN sets the hours ({@link WorkHours}, one row in
 * {@code working_hours}); the standard plan is Monday to Saturday, 8:00 to 17:00, check-in from 7:30 (after
 * 9:00 counts as late), one lunch break of up to 45 minutes started between 12:00 and 14:30, and pickups until
 * 15:00. Weekdays outside the working days, and the holidays a SYSTEM_ADMIN lists, are closed. Nobody works at
 * night.
 */
@Component
public class WorkingDay {

    public static final ZoneId ZONE = WorkHours.ZONE;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM");

    private final Clock clock;
    private final ClosedDayRepository closedDays;
    private final WorkingHoursRepository workingHours;

    @Autowired
    public WorkingDay(Clock clock, ClosedDayRepository closedDays, WorkingHoursRepository workingHours) {
        this.clock = clock;
        this.closedDays = closedDays;
        this.workingHours = workingHours;
    }

    /** For unit tests: the standard hours, never saved. */
    public WorkingDay(Clock clock, ClosedDayRepository closedDays) {
        this(clock, closedDays, null);
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private LocalTime timeNow() {
        return LocalTime.now(clock.withZone(ZONE));
    }

    /** The hours as the admin last saved them, or the standard plan. */
    @Transactional(readOnly = true)
    public WorkHours hours() {
        if (workingHours == null) {
            return WorkHours.STANDARD;
        }
        return workingHours.findById(WorkingHoursSetting.ID).map(WorkingHoursSetting::toHours)
                .orElse(WorkHours.STANDARD);
    }

    /** Saves new hours (already validated) and returns today's working day under them. */
    @Transactional
    public WorkdayResponse saveHours(WorkHours hours, UUID by) {
        hours.validate();
        WorkingHoursSetting setting = workingHours.findById(WorkingHoursSetting.ID)
                .orElseGet(() -> WorkingHoursSetting.of(WorkHours.STANDARD));
        setting.apply(hours, by, now());
        workingHours.save(setting);
        return describeToday();
    }

    /** Why {@code day} is closed (the weekday's name or the holiday's name), or empty on a working day. */
    @Transactional(readOnly = true)
    public Optional<String> closedReason(LocalDate day) {
        return closedReason(day, hours());
    }

    private Optional<String> closedReason(LocalDate day, WorkHours hours) {
        if (!hours.isWorkingDay(day.getDayOfWeek())) {
            return Optional.of(weekdayName(day.getDayOfWeek()));
        }
        return closedDays.findById(day).map(ClosedDay::getName);
    }

    /** "SUNDAY" -> "Sunday". */
    public static String weekdayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /** The first working day after {@code day}. */
    @Transactional(readOnly = true)
    public LocalDate nextWorkingDay(LocalDate day) {
        return nextWorkingDay(day, hours());
    }

    private LocalDate nextWorkingDay(LocalDate day, WorkHours hours) {
        LocalDate next = day.plusDays(1);
        // A long run of listed holidays is not realistic; the bound only guards against bad data.
        for (int i = 0; i < 60 && closedReason(next, hours).isPresent(); i++) {
            next = next.plusDays(1);
        }
        return next;
    }

    /** A shift starts only on a working day, from check-in time until closing. */
    @Transactional(readOnly = true)
    public void requireShiftCanStart() {
        WorkHours hours = hours();
        LocalDate today = today();
        Optional<String> closed = closedReason(today, hours);
        if (closed.isPresent()) {
            throw new ShiftRuleException("Closed today (%s). The next working day is %s."
                    .formatted(closed.get(), DAY.format(nextWorkingDay(today, hours))));
        }
        LocalTime now = timeNow();
        if (now.isBefore(hours.shiftFrom())) {
            throw new ShiftRuleException("Shifts start from %s.".formatted(TIME.format(hours.shiftFrom())));
        }
        if (!now.isBefore(hours.closesAt())) {
            throw new ShiftRuleException("The working day ended at %s. The next working day is %s."
                    .formatted(TIME.format(hours.closesAt()), DAY.format(nextWorkingDay(today, hours))));
        }
    }

    /** Whether it is too late in the day to send a courier for a new pickup. */
    @Transactional(readOnly = true)
    public boolean pickupsClosed() {
        return !timeNow().isBefore(hours().pickupCutoff());
    }

    /** One lunch break per shift, started within the lunch window. */
    @Transactional(readOnly = true)
    public void requireBreakCanStart(CourierShift shift) {
        if (shift.getBreakStartedAt() != null) {
            throw new ShiftRuleException("You have had your break today.");
        }
        WorkHours hours = hours();
        LocalTime now = timeNow();
        if (now.isBefore(hours.breakFrom()) || now.isAfter(hours.breakUntil())) {
            throw new ShiftRuleException("Lunch break is between %s and %s."
                    .formatted(TIME.format(hours.breakFrom()), TIME.format(hours.breakUntil())));
        }
    }

    /** "15:00" style, for messages. */
    public static String time(LocalTime time) {
        return TIME.format(time);
    }

    @Transactional(readOnly = true)
    public WorkdayResponse describeToday() {
        WorkHours hours = hours();
        LocalDate today = today();
        Optional<String> closed = closedReason(today, hours);
        LocalTime now = timeNow();
        boolean shiftCanStart = closed.isEmpty() && !now.isBefore(hours.shiftFrom()) && now.isBefore(hours.closesAt());
        return new WorkdayResponse(today, closed.isEmpty(), closed.orElse(null), nextWorkingDay(today, hours),
                shiftCanStart, hours.workingDays().stream().sorted().toList(), hours.opensAt(), hours.closesAt(),
                hours.shiftFrom(), hours.lateAfter(), hours.breakFrom(), hours.breakUntil(), hours.breakMinutes(),
                hours.pickupCutoff(), hours.sameDayCutoff(), upcomingClosedDays());
    }

    @Transactional(readOnly = true)
    public List<WorkdayResponse.ClosedDayResponse> upcomingClosedDays() {
        return closedDays.findByDayGreaterThanEqualOrderByDay(today()).stream()
                .map(day -> new WorkdayResponse.ClosedDayResponse(day.getDay(), day.getName()))
                .toList();
    }
}
