package com.swiftdrop.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contact coordinates resolved from auth-iam-service internal lookup or fake fallback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipientContact(
        String email
) {
}
