package com.swiftdrop.pricing.service.strategy;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** STANDARD delivery: the base fee with no speed surcharge. */
@Component
public class StandardPricingStrategy implements PricingStrategy {

    @Override
    public boolean supports(OrderSpeed speed) {
        return speed == OrderSpeed.STANDARD;
    }

    @Override
    public BigDecimal apply(BigDecimal subtotal, PricingZone zone) {
        return subtotal;
    }
}
