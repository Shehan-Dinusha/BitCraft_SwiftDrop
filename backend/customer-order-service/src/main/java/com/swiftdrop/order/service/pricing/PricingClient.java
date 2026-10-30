package com.swiftdrop.order.service.pricing;

import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;

/**
 * Source of delivery fee estimates (FR-C2). Implementations decide where the numbers come from, so the
 * order service does not depend on how pricing is calculated.
 */
public interface PricingClient {

    FeeEstimateResponse estimate(FeeEstimateRequest request);
}
