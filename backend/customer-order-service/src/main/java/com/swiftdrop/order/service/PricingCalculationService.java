package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service to calculate pre-confirmation fee estimates (FR-C2)
 * using the locked SwiftDrop tariff matrix.
 */
@Service
@Slf4j
public class PricingCalculationService {

    public static final String CURRENT_RULE_VERSION = "PRICING-2026-V1";
    private static final BigDecimal VOLUMETRIC_DIVISOR = new BigDecimal("5000");

    public FeeEstimateResponse calculateEstimate(FeeEstimateRequest request) {
        boolean isIntraDistrict = request.getPickupDistrict() == request.getDeliveryDistrict();

        // 1. Calculate volumetric weight: (L * W * H) / 5000
        BigDecimal volumetricWeight = BigDecimal.ZERO;
        if (request.getLengthCm() != null && request.getWidthCm() != null && request.getHeightCm() != null
                && request.getLengthCm().compareTo(BigDecimal.ZERO) > 0
                && request.getWidthCm().compareTo(BigDecimal.ZERO) > 0
                && request.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal volume = request.getLengthCm().multiply(request.getWidthCm()).multiply(request.getHeightCm());
            volumetricWeight = volume.divide(VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP);
        }

        BigDecimal chargeableWeight = request.getActualWeightKg().max(volumetricWeight)
                .setScale(2, RoundingMode.HALF_UP);

        // 2. Base fee and extra-kg fee
        BigDecimal baseFee = isIntraDistrict ? new BigDecimal("350.00") : new BigDecimal("550.00");
        BigDecimal perKgFee = isIntraDistrict ? new BigDecimal("80.00") : new BigDecimal("120.00");

        BigDecimal weightFee = BigDecimal.ZERO;
        if (chargeableWeight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraKg = chargeableWeight.subtract(BigDecimal.ONE)
                    .setScale(0, RoundingMode.CEILING);
            weightFee = extraKg.multiply(perKgFee).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal standardSubtotal = baseFee.add(weightFee);

        // 3. Service speed multiplier
        OrderSpeed speed = request.getSpeed() != null ? request.getSpeed() : OrderSpeed.STANDARD;
        BigDecimal speedMultiplier = BigDecimal.ONE;
        if (speed == OrderSpeed.EXPRESS) {
            speedMultiplier = new BigDecimal("1.50");
        } else if (speed == OrderSpeed.SAME_DAY) {
            speedMultiplier = new BigDecimal("2.00");
        }

        BigDecimal totalFee = standardSubtotal.multiply(speedMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal speedSurcharge = totalFee.subtract(standardSubtotal).setScale(2, RoundingMode.HALF_UP);

        return FeeEstimateResponse.builder()
                .pickupDistrict(request.getPickupDistrict())
                .deliveryDistrict(request.getDeliveryDistrict())
                .intraDistrict(isIntraDistrict)
                .actualWeightKg(request.getActualWeightKg().setScale(2, RoundingMode.HALF_UP))
                .volumetricWeightKg(volumetricWeight)
                .chargeableWeightKg(chargeableWeight)
                .speed(speed)
                .baseFee(baseFee)
                .weightFee(weightFee)
                .speedSurcharge(speedSurcharge)
                .estimatedFee(totalFee)
                .currency("LKR")
                .pricingRuleVersionId(CURRENT_RULE_VERSION)
                .build();
    }
}
