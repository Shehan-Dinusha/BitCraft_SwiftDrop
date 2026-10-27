package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Merchant API key provisioning request")
public record CreateApiKeyRequest(
        @NotBlank(message = "Key name or description is required")
        @Schema(description = "Descriptive key label", example = "Production ERP Ingestion Key")
        String keyName,

        @Schema(description = "Optional key expiration duration in days (null for indefinite)", example = "90")
        Integer expiresInDays
) {
}
