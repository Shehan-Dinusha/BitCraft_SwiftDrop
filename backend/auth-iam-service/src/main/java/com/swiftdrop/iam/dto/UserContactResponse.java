package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight user contact projection returned by the internal contact-lookup endpoint.
 *
 * <p>This record is intentionally minimal — it contains only the data that peer services
 * (e.g. notification-service) need to reach a user, and nothing that would expose PII
 * beyond what is strictly necessary.
 *
 * <p><strong>Security note:</strong> this response must never be served through the public
 * API gateway.  The endpoint is bound to the IAM service's internal port only and is
 * blocked at the gateway level.
 */
@Schema(description = "Minimal contact information for internal service-to-service use only")
public record UserContactResponse(

        @Schema(description = "Primary email address", example = "john.doe@example.com")
        String email,

        @Schema(description = "Phone number in E.164 format; null if not provided", example = "+94771234567")
        String phone
) {}

