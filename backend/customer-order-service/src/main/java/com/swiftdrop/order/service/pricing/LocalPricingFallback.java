package com.swiftdrop.order.service.pricing;

import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.service.PricingCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Prices estimates with the order service's own copy of the tariff. Kept only until the pricing
 * service's endpoint is the single source of pricing.
 */
@Component
@RequiredArgsConstructor
public class LocalPricingFallback implements PricingClient {

    private final PricingCalculationService pricingCalculationService;

    @Override
    public FeeEstimateResponse estimate(FeeEstimateRequest request) {
        return pricingCalculationService.calculateEstimate(request);
    }
}
