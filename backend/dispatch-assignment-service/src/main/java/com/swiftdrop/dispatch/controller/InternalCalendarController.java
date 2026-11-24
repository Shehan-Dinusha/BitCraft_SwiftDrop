package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.dispatch.domain.entity.ClosedDay;
import com.swiftdrop.dispatch.domain.repository.ClosedDayRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Service-to-service only: the order service counts working days for its delivery promise. Never routed. */
@RestController
@RequestMapping("/internal/v1/calendar")
@Tag(name = "Internal Calendar", description = "Service-to-service endpoints. Trusted network only.")
public class InternalCalendarController {

    private final ClosedDayRepository closedDays;

    public InternalCalendarController(ClosedDayRepository closedDays) {
        this.closedDays = closedDays;
    }

    @GetMapping("/closed-days")
    @Operation(summary = "The listed holidays between two days, both included (Sundays are not listed)")
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
