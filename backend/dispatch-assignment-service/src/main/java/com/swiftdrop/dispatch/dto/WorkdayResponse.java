package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Today's working day: whether it is open, the hours, the lunch break, and the coming holidays. "
        + "Times are Sri Lanka time.")
public record WorkdayResponse(
        @Schema(description = "Today, in Sri Lanka", example = "2026-11-24")
        LocalDate date,

        @Schema(description = "Whether today is a working day")
        boolean open,

        @Schema(description = "Why today is closed: \"Sunday\" or the holiday's name; null on a working day",
                example = "Poya Day")
        String closedReason,

        @Schema(description = "The next working day after today", example = "2026-11-25")
        LocalDate nextWorkingDay,

        @Schema(description = "Whether a courier can start a shift right now")
        boolean shiftCanStart,

        @Schema(description = "Working day opens", type = "string", example = "08:00")
        LocalTime opensAt,

        @Schema(description = "Working day closes", type = "string", example = "17:00")
        LocalTime closesAt,

        @Schema(description = "Earliest shift start", type = "string", example = "07:30")
        LocalTime shiftFrom,

        @Schema(description = "Lunch break can start from", type = "string", example = "12:00")
        LocalTime breakFrom,

        @Schema(description = "Lunch break can start until", type = "string", example = "14:30")
        LocalTime breakUntil,

        @Schema(description = "Lunch break length in minutes", example = "45")
        int breakMinutes,

        @Schema(description = "New pickups are given out until", type = "string", example = "15:00")
        LocalTime pickupCutoff,

        @Schema(description = "Listed holidays from today on")
        List<ClosedDayResponse> closedDays
) {
    @Schema(description = "A holiday on which nobody works.")
    public record ClosedDayResponse(
            @Schema(description = "The day", example = "2026-12-04")
            LocalDate day,

            @Schema(description = "Why it is closed", example = "Unduvap Poya Day")
            String name
    ) {
    }
}
