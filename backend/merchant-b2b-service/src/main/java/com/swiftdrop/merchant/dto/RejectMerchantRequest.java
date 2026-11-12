package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Rejection of a pending business application")
public record RejectMerchantRequest(
        @NotBlank @Size(max = 500) @Schema(description = "Required rejection reason") String reason
) {}
