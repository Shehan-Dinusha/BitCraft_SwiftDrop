package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.dispatch.dto.ClosedDayRequest;
import com.swiftdrop.dispatch.dto.WorkdayResponse;
import com.swiftdrop.dispatch.dto.WorkingHoursRequest;
import com.swiftdrop.dispatch.service.WorkHours;
import com.swiftdrop.dispatch.service.ClosedDayService;
import com.swiftdrop.dispatch.service.WorkingDay;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dispatch/calendar")
@Tag(name = "WorkingDay", description = "Working hours, the lunch break and the holidays on which nobody works")
public class WorkingDayController {

    private final WorkingDay workingDay;
    private final ClosedDayService closedDayService;

    public WorkingDayController(WorkingDay workingDay, ClosedDayService closedDayService) {
        this.workingDay = workingDay;
        this.closedDayService = closedDayService;
    }

    @GetMapping("/today")
    @Operation(summary = "Today's working day",
            description = "Whether today is open, the hours, the lunch break and the coming holidays, in Sri Lanka "
                    + "time, as the SYSTEM_ADMIN set them (standard: Monday to Saturday, 8:00 to 17:00). For "
                    + "COURIER, DISPATCHER and SYSTEM_ADMIN.")
    public ApiResponse<WorkdayResponse> today(HttpServletRequest httpRequest) {
        CallerContext.from(httpRequest::getHeader)
                .requireRole(UserRole.COURIER, UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        return ApiResponse.success(workingDay.describeToday(), "Working day retrieved");
    }

    @PutMapping("/hours")
    @Operation(summary = "Change the working hours",
            description = "The working days, hours, lunch break and cut-offs couriers work to. They apply at once: "
                    + "shift starts, breaks, late starts, pickups given out and the delivery day promised at booking. "
                    + "400 when the hours do not make a workable day. SYSTEM_ADMIN only. Returns today's working day.")
    public ApiResponse<WorkdayResponse> updateHours(@Valid @RequestBody WorkingHoursRequest request,
                                                    HttpServletRequest httpRequest) {
        CallerContext caller = requireAdmin(httpRequest);
        return ApiResponse.success(workingDay.saveHours(request.toHours(), caller.userId()), "Working hours saved");
    }

    @DeleteMapping("/hours")
    @Operation(summary = "Restore the standard working hours",
            description = "Monday to Saturday, 8:00 to 17:00, check-in from 7:30 (late after 9:00), lunch of 45 minutes "
                    + "started between 12:00 and 14:30, pickups until 15:00, Same day until 11:00. SYSTEM_ADMIN only.")
    public ApiResponse<WorkdayResponse> resetHours(HttpServletRequest httpRequest) {
        CallerContext caller = requireAdmin(httpRequest);
        return ApiResponse.success(workingDay.saveHours(WorkHours.STANDARD, caller.userId()),
                "Standard working hours restored");
    }

    @PostMapping("/closed-days")
    @Operation(summary = "Close a holiday",
            description = "Nobody works that day: shifts cannot start. Today or later, a working weekday. "
                    + "SYSTEM_ADMIN only. Returns the holidays from today on.")
    public ApiResponse<List<WorkdayResponse.ClosedDayResponse>> addClosedDay(
            @Valid @RequestBody ClosedDayRequest request, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return ApiResponse.success(closedDayService.add(request), "Holiday closed");
    }

    @DeleteMapping("/closed-days/{day}")
    @Operation(summary = "Reopen a holiday",
            description = "Today or later only. SYSTEM_ADMIN only. Returns the holidays from today on.")
    public ApiResponse<List<WorkdayResponse.ClosedDayResponse>> removeClosedDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return ApiResponse.success(closedDayService.remove(day), "Holiday reopened");
    }

    private static CallerContext requireAdmin(HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        caller.requireRole(UserRole.SYSTEM_ADMIN);
        return caller;
    }
}
