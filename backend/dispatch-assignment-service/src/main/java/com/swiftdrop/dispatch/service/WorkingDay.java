package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.repository.ClosedDayRepository;
import com.swiftdrop.dispatch.dto.WorkdayResponse;
import com.swiftdrop.dispatch.exception.ShiftRuleException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * The courier working day: Monday to Saturday, 8:00 to 17:00 Sri Lanka time. Sundays and the holidays a
 * SYSTEM_ADMIN lists are closed. A courier checks in from 7:30 (after 9:00 counts as late), takes one lunch
 * break of up to 45 minutes between 12:00 and 14:30, and nobody works at night.
 */
@Component
public class WorkingDay {

    public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    public static final LocalTime OPENS = LocalTime.of(8, 0);
    public static final LocalTime CLOSES = LocalTime.of(17, 0);
    /** Couriers may check in a little before opening. */
    public static final LocalTime SHIFT_FROM = LocalTime.of(7, 30);
    /** A shift started after this is shown to the dispatcher as a late start. */
    public static final LocalTime LATE_AFTER = LocalTime.of(9, 0);
    public static final LocalTime BREAK_FROM = LocalTime.of(12, 0);
    public static final LocalTime BREAK_UNTIL = LocalTime.of(14, 30);
    public static final Duration BREAK_LENGTH = Duration.ofMinutes(45);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM");

    private final Clock clock;
    private final ClosedDayRepository closedDays;

    public WorkingDay(Clock clock, ClosedDayRepository closedDays) {
        this.clock = clock;
        this.closedDays = closedDays;
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

    /** Why {@code day} is closed ("Sunday" or the holiday's name), or empty on a working day. */
    @Transactional(readOnly = true)
    public Optional<String> closedReason(LocalDate day) {
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return Optional.of("Sunday");
        }
        return closedDays.findById(day).map(ClosedDay::getName);
    }

    /** The first working day after {@code day}. */
    @Transactional(readOnly = true)
    public LocalDate nextWorkingDay(LocalDate day) {
        LocalDate next = day.plusDays(1);
        // A long run of listed holidays is not realistic; the bound only guards against bad data.
        for (int i = 0; i < 60 && closedReason(next).isPresent(); i++) {
            next = next.plusDays(1);
        }
        return next;
    }

    /** A shift starts only on a working day, from 7:30 until closing. */
    @Transactional(readOnly = true)
    public void requireShiftCanStart() {
        LocalDate today = today();
        Optional<String> closed = closedReason(today);
        if (closed.isPresent()) {
            throw new ShiftRuleException("Closed today (%s). The next working day is %s."
                    .formatted(closed.get(), DAY.format(nextWorkingDay(today))));
        }
        LocalTime now = timeNow();
        if (now.isBefore(SHIFT_FROM)) {
            throw new ShiftRuleException("Shifts start from %s.".formatted(TIME.format(SHIFT_FROM)));
        }
        if (!now.isBefore(CLOSES)) {
            throw new ShiftRuleException("The working day ended at %s. The next working day is %s."
                    .formatted(TIME.format(CLOSES), DAY.format(nextWorkingDay(today))));
        }
    }

    /** One lunch break per shift, started between 12:00 and 14:30. */
    public void requireBreakCanStart(CourierShift shift) {
        if (shift.getBreakStartedAt() != null) {
            throw new ShiftRuleException("You have had your break today.");
        }
        LocalTime now = timeNow();
        if (now.isBefore(BREAK_FROM) || now.isAfter(BREAK_UNTIL)) {
            throw new ShiftRuleException("Lunch break is between %s and %s."
                    .formatted(TIME.format(BREAK_FROM), TIME.format(BREAK_UNTIL)));
        }
    }

    /** When a break started at {@code started} should end. */
    public static Instant breakEndsAt(Instant started) {
        return started == null ? null : started.plus(BREAK_LENGTH);
    }

    /** Whether a shift started at {@code started} started after 9:00. */
    public static boolean startedLate(Instant started) {
        return started != null && started.atZone(ZONE).toLocalTime().isAfter(LATE_AFTER);
    }

    @Transactional(readOnly = true)
    public WorkdayResponse describeToday() {
        LocalDate today = today();
        Optional<String> closed = closedReason(today);
        LocalTime now = timeNow();
        boolean shiftCanStart = closed.isEmpty() && !now.isBefore(SHIFT_FROM) && now.isBefore(CLOSES);
        return new WorkdayResponse(today, closed.isEmpty(), closed.orElse(null), nextWorkingDay(today),
                shiftCanStart, OPENS, CLOSES, SHIFT_FROM, BREAK_FROM, BREAK_UNTIL, (int) BREAK_LENGTH.toMinutes(),
                upcomingClosedDays());
    }

    @Transactional(readOnly = true)
    public List<WorkdayResponse.ClosedDayResponse> upcomingClosedDays() {
        return closedDays.findByDayGreaterThanEqualOrderByDay(today()).stream()
                .map(day -> new WorkdayResponse.ClosedDayResponse(day.getDay(), day.getName()))
                .toList();
    }
}
