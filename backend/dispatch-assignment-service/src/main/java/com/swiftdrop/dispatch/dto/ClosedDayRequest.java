package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "A holiday to close: nobody works that day.")
public record ClosedDayRequest(
        @NotNull(message = "Choose a day")
        @Schema(description = "The day; today or later, and not a Sunday", example = "2026-12-04")
        LocalDate day,

        @NotBlank(message = "Name the holiday")
        @Size(max = 64, message = "Use at most 64 characters")
        @Schema(description = "Why it is closed", example = "Unduvap Poya Day")
        String name
) {
}
