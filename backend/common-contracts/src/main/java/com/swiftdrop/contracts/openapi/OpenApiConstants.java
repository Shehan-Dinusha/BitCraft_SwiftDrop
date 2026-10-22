package com.swiftdrop.contracts.openapi;

/**
 * Shared OpenAPI metadata and security identifiers used by SwiftDrop services.
 */
public final class OpenApiConstants {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";
    public static final String CORRELATION_ID_HEADER_DESCRIPTION =
            "Correlation identifier propagated across gateway and downstream services.";
    public static final String CONTACT_NAME = "SwiftDrop Platform Team";
    public static final String CONTACT_URL = "https://swiftdrop.example.com";
    public static final String LICENSE_NAME = "Proprietary";
    public static final String LICENSE_URL = "https://swiftdrop.example.com/license";

    private OpenApiConstants() {
    }
}
