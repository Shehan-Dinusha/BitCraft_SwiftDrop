package com.swiftdrop.dispatch.dto;

import com.swiftdrop.dispatch.service.WorkHours;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

@Schema(description = "The courier working hours, in Sri Lanka time. The day must close by 20:00 and check-in "
        + "may open from 7:00; the lunch window and both cut-offs must be within working hours.")
public record WorkingHoursRequest(
        @NotEmpty(message = "Choose at least one working day")
        @Schema(description = "The weekdays couriers work",
                example = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\"]")
        Set<DayOfWeek> workingDays,

        @NotNull @Schema(description = "Working day opens", type = "string", example = "08:00")
        LocalTime opensAt,

        @NotNull @Schema(description = "Working day closes", type = "string", example = "17:00")
        LocalTime closesAt,

        @NotNull @Schema(description = "Earliest shift start", type = "string", example = "07:30")
        LocalTime shiftFrom,

        @NotNull @Schema(description = "A shift started after this shows as late", type = "string", example = "09:00")
        LocalTime lateAfter,

        @NotNull @Schema(description = "Lunch break can start from", type = "string", example = "12:00")
        LocalTime breakFrom,

        @NotNull @Schema(description = "Lunch break can start until", type = "string", example = "14:30")
        LocalTime breakUntil,

        @Min(value = 10, message = "The lunch break is at least 10 minutes")
        @Max(value = 120, message = "The lunch break is at most 120 minutes")
        @Schema(description = "Lunch break length in minutes", example = "45")
        int breakMinutes,

        @NotNull @Schema(description = "New pickups are given out until", type = "string", example = "15:00")
        LocalTime pickupCutoff,

        @NotNull @Schema(description = "Same day parcels are booked before this", type = "string", example = "11:00")
        LocalTime sameDayCutoff
) {
    public WorkHours toHours() {
        return new WorkHours(workingDays, opensAt, closesAt, shiftFrom, lateAfter, breakFrom, breakUntil, breakMinutes,
                pickupCutoff, sameDayCutoff);
    }
}
