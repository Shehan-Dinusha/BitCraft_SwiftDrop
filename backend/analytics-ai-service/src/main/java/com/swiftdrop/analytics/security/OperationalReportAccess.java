package com.swiftdrop.analytics.security;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import java.lang.annotation.*;

/** Requires gateway-verified operational identity; downstream ports must be internal. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Verified gateway identity required"),
        @ApiResponse(responseCode = "403", description = "DISPATCHER or SYSTEM_ADMIN required")
})
public @interface OperationalReportAccess {}
