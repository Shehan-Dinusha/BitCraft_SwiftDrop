package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A shop says everything it has booked so far is packed.")
public record InternalPickupReadyRequest(
        @NotBlank @Size(max = 150) @Schema(description = "Shop name", example = "Nimal Fashion") String merchantName
) {
}
