package com.swiftdrop.analytics.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Schema(description = "Required ISO dates with inclusive boundaries, maximum 90 days.")
public record ReportDateRange(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "First included Colombo date", example = "2026-11-08") LocalDate from,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Last included Colombo date", example = "2026-11-09") LocalDate to) {
    @AssertTrue(message = "Provide an inclusive date range of 1 to 90 days")
    public boolean isValidRange() {
        return from == null || to == null || (!to.isBefore(from) && ChronoUnit.DAYS.between(from, to) < 90);
    }
}
