package com.swiftdrop.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Internal result of merchant API key validation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Merchant API key validation result.")
public record MerchantApiKeyValidationResult(

        @JsonProperty("valid")
        @Schema(description = "Whether the API key is active and belongs to a merchant.")
        boolean valid,

        @JsonProperty("merchantId")
        @Schema(description = "Merchant identifier when the key is valid.")
        UUID merchantId,

        @JsonProperty("reason")
        @Schema(description = "Machine-readable rejection reason when the key is invalid.")
        String reason

) {
    public static MerchantApiKeyValidationResult valid(UUID merchantId) {
        return new MerchantApiKeyValidationResult(true, merchantId, null);
    }

    public static MerchantApiKeyValidationResult invalid(String reason) {
        return new MerchantApiKeyValidationResult(false, null, reason);
    }
}
