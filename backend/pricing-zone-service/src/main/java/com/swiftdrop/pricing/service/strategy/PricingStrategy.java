package com.swiftdrop.pricing.service.strategy;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.pricing.domain.entity.PricingZone;

import java.math.BigDecimal;

/**
 * Strategy pattern for the delivery-speed surcharge. Each speed is its own class so a new speed
 * can be added without editing the pricing engine (open/closed principle); the engine picks the
 * strategy whose {@link #supports} matches the order.
 */
public interface PricingStrategy {

    /** @return true if this strategy prices the given delivery speed */
    boolean supports(OrderSpeed speed);

    /**
     * Applies the speed surcharge to the distance-and-weight subtotal.
     *
     * @param subtotal fee before the speed surcharge, in LKR
     * @param zone     pickup zone, which holds the zone-specific multipliers
     * @return the fee after the surcharge, in LKR
     */
    BigDecimal apply(BigDecimal subtotal, PricingZone zone);
}
