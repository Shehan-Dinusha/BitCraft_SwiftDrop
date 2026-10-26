package com.swiftdrop.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Internal request sent by the API Gateway to the merchant service to validate
 * a merchant API key. This request must never be routed from the public edge.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Merchant API key validation request.")
public record MerchantApiKeyValidationRequest(

        @JsonProperty("apiKey")
        @NotBlank
        @Schema(description = "Raw merchant API key.", example = "sk_live_...")
        String apiKey

) {
}
