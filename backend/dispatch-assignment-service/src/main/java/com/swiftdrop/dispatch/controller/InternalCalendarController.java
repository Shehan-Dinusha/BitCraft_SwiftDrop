package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import com.swiftdrop.dispatch.domain.repository.ClosedDayRepository;
import com.swiftdrop.dispatch.service.WorkHours;
import com.swiftdrop.dispatch.service.WorkingDay;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Service-to-service only: the order service counts working days for its delivery promise. Never routed. */
@RestController
@RequestMapping("/internal/v1/calendar")
@Tag(name = "Internal Calendar", description = "Service-to-service endpoints. Trusted network only.")
public class InternalCalendarController {

    private final ClosedDayRepository closedDays;
    private final WorkingDay workingDay;

    public InternalCalendarController(ClosedDayRepository closedDays, WorkingDay workingDay) {
        this.closedDays = closedDays;
        this.workingDay = workingDay;
    }

    /** What the order service needs for its delivery promise: the working weekdays and the two cut-offs. */
    public record CalendarRules(List<DayOfWeek> workingDays, LocalTime sameDayCutoff, LocalTime pickupCutoff) {
    }

    @GetMapping("/rules")
    @Operation(summary = "The working weekdays and the booking cut-offs the SYSTEM_ADMIN set")
    public ApiResponse<CalendarRules> rules() {
        WorkHours hours = workingDay.hours();
        return ApiResponse.success(new CalendarRules(hours.workingDays().stream().sorted().toList(),
                hours.sameDayCutoff(), hours.pickupCutoff()), "Calendar rules retrieved");
    }

    @GetMapping("/closed-days")
    @Operation(summary = "The listed holidays between two days, both included (weekdays outside the working days are not listed)")
    public ApiResponse<List<LocalDate>> closedDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(366))) {
            throw new IllegalArgumentException("Ask for at most a year, from before to");
        }
        return ApiResponse.success(closedDays.findByDayBetweenOrderByDay(from, to).stream()
                .map(ClosedDay::getDay).toList(), "Closed days retrieved");
    }
}
