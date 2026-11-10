package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Schema(description = "Merchant order; merchant identity is supplied only by the trusted gateway")
public record B2bOrderRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:/#-]+")
        @Schema(description = "Merchant reference used for idempotency") String externalRef,
        @Valid @NotNull @Schema(description = "Pickup details") B2bAddress pickup,
        @Valid @NotNull @Schema(description = "Delivery details") B2bAddress delivery,
        @Valid @NotNull @Schema(description = "Parcel details") B2bParcel parcel,
        @Schema(description = "STANDARD (default) or EXPRESS") String speed
) {}
