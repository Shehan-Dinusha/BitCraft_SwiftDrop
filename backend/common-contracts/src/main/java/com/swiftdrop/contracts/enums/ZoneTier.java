package com.swiftdrop.contracts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Geographic zone tiers that determine pricing multipliers and courier availability.
 * Managed by the Pricing &amp; Zone Service.
 */
@Schema(description = "Geographic tier used for pricing and courier availability.", example = "CORE_URBAN")
public enum ZoneTier {
    CORE_URBAN,
    SUBURBAN,
    EXTENDED,
    RESTRICTED
}
