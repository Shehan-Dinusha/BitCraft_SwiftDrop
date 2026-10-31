package com.swiftdrop.pricing.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.repository.DistrictZoneMappingRepository;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPricingEngine {

    public static final String CURRENT_RULE_VERSION = "PRICING-2026-V1";
    private static final BigDecimal VOLUMETRIC_DIVISOR = new BigDecimal("5000");

    private final DistrictZoneMappingRepository districtZoneMappingRepository;
    private final PricingZoneRepository pricingZoneRepository;

    public PricingQuoteResponse calculateQuote(PricingQuoteRequest request) {
        log.info("Calculating delivery quote from {} to {}, weight={}kg, speed={}",
                request.getPickupDistrict(), request.getDeliveryDistrict(),
                request.getActualWeightKg(), request.getSpeed());

        // 1. Resolve zones
        String pickupZoneCode = districtZoneMappingRepository.findByDistrict(request.getPickupDistrict().name())
                .map(DistrictZoneMapping::getZoneCode)
                .orElse("ZONE_WESTERN");

        String deliveryZoneCode = districtZoneMappingRepository.findByDistrict(request.getDeliveryDistrict().name())
                .map(DistrictZoneMapping::getZoneCode)
                .orElse("ZONE_WESTERN");

        PricingZone pickupZone = pricingZoneRepository.findByZoneCode(pickupZoneCode)
                .orElse(null);

        boolean isIntraDistrict = request.getPickupDistrict() == request.getDeliveryDistrict();

        // 2. Volumetric and Chargeable weight calculation
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

        // 3. Base fee & Per-kg surcharge calculation
        BigDecimal baseFee;
        BigDecimal perKgFee;

        if (isIntraDistrict) {
            baseFee = pickupZone != null ? pickupZone.getBaseFee() : new BigDecimal("350.00");
            perKgFee = pickupZone != null ? pickupZone.getPerKgFee() : new BigDecimal("80.00");
        } else {
            // Inter-district baseline: 550.00 base, 120.00 per extra kg
            baseFee = new BigDecimal("550.00");
            perKgFee = new BigDecimal("120.00");
        }

        BigDecimal weightFee = BigDecimal.ZERO;
        if (chargeableWeight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraKg = chargeableWeight.subtract(BigDecimal.ONE)
                    .setScale(0, RoundingMode.CEILING); // standard logistics billing rounds up extra fractional kg
            weightFee = extraKg.multiply(perKgFee).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal standardSubtotal = baseFee.add(weightFee);

        // 4. Service speed multiplier
        OrderSpeed speed = request.getSpeed() != null ? request.getSpeed() : OrderSpeed.STANDARD;
        BigDecimal speedMultiplier = BigDecimal.ONE;
        if (speed == OrderSpeed.EXPRESS) {
            speedMultiplier = pickupZone != null ? pickupZone.getExpressMultiplier() : new BigDecimal("1.50");
        } else if (speed == OrderSpeed.SAME_DAY) {
            speedMultiplier = pickupZone != null ? pickupZone.getSameDayMultiplier() : new BigDecimal("2.00");
        }

        BigDecimal totalFee = standardSubtotal.multiply(speedMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal speedSurcharge = totalFee.subtract(standardSubtotal).setScale(2, RoundingMode.HALF_UP);

        return PricingQuoteResponse.builder()
                .pickupDistrict(request.getPickupDistrict())
                .deliveryDistrict(request.getDeliveryDistrict())
                .pickupZone(pickupZoneCode)
                .deliveryZone(deliveryZoneCode)
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
