package com.swiftdrop.order.controller;

import java.util.UUID;

/** Shared header parsing for the order controllers. */
final class HeaderParsing {

    private HeaderParsing() {
    }

    /** Parses a UUID header value, returning null when it is missing, blank or malformed. */
    static UUID parseUuidSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
