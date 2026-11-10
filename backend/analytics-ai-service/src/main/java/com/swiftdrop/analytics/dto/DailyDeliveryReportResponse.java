package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Inclusive daily event-time counts, zero-filled and sorted by date.")
public record DailyDeliveryReportResponse(
        @Schema(description = "First included date") LocalDate from,
        @Schema(description = "Last included date") LocalDate to,
        @Schema(description = "Business reporting timezone", example = "Asia/Colombo") String timezone,
        @Schema(description = "One row for every requested day") List<Day> days) {
    @Schema(description = "Distinct creations and outcomes on their respective occurrence dates; not a creation cohort.")
    public record Day(
            @Schema(description = "Colombo reporting date") LocalDate date,
            @Schema(description = "Distinct orders created") long created,
            @Schema(description = "Distinct delivered outcomes") long delivered,
            @Schema(description = "Distinct final failed outcomes") long failed) {}
}
