package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Merchant API key details response")
public record ApiKeyResponse(
        @Schema(description = "Unique API key identifier")
        UUID keyId,

        @Schema(description = "Owning merchant UUID")
        UUID merchantId,

        @Schema(description = "Descriptive key label")
        String keyName,

        @Schema(description = "Masked key prefix for identification (e.g. sk_live_7a9f...)")
        String keyPrefix,

        @Schema(description = "Whether the key is active")
        boolean active,

        @Schema(description = "Key creation timestamp")
        Instant createdAt,

        @Schema(description = "Expiration timestamp if configured")
        Instant expiresAt,

        @Schema(description = "Last timestamp this key authenticated a request")
        Instant lastUsedAt,

        @Schema(description = "Revocation timestamp if revoked")
        Instant revokedAt,

        @Schema(description = "Raw secret API key. ONLY RETURNED ONCE upon creation; never persisted in plaintext.")
        String rawApiKey
) {
}
