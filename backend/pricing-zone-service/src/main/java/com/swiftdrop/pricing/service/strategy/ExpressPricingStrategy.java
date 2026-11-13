package com.swiftdrop.pricing.service.strategy;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ExpressPricingStrategy implements PricingStrategy {

    @Override
    public boolean supports(OrderSpeed speed) {
        return speed == OrderSpeed.EXPRESS;
    }

    @Override
    public BigDecimal apply(BigDecimal subtotal, PricingZone zone) {
        return subtotal.multiply(zone.getExpressMultiplier());
    }
}
