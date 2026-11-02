package com.swiftdrop.contracts.security;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Shared interpretation of per-user Redis revocation cutoffs by IAM and gateway. */
public final class JwtSessionRevocation {

    /** ISO-8601 issuance time preserving precision lost by the standard JWT iat. */
    public static final String ISSUED_AT_CLAIM = "sessionIssuedAt";

    private JwtSessionRevocation() {
    }

    public static boolean isRevoked(String cutoffValue, String sessionIssuedAt, Instant legacyIssuedAt) {
        if (cutoffValue == null) {
            return false;
        }
        try {
            Instant cutoff = Instant.parse(cutoffValue);
            Instant issuedAt = sessionIssuedAt == null ? legacyIssuedAt : Instant.parse(sessionIssuedAt);
            return issuedAt == null || !issuedAt.isAfter(cutoff);
        } catch (DateTimeParseException exception) {
            // Honor legacy "1" blacklist markers and reject malformed timestamps.
            return true;
        }
    }
}
