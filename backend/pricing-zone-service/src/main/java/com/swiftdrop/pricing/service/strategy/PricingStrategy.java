package com.swiftdrop.pricing.service.strategy;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.pricing.domain.entity.PricingZone;

import java.math.BigDecimal;

public interface PricingStrategy {

    boolean supports(OrderSpeed speed);

    BigDecimal apply(BigDecimal subtotal, PricingZone zone);
}
