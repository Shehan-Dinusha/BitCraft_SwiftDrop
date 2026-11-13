package com.swiftdrop.pricing.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.entity.TariffRule;
import com.swiftdrop.pricing.domain.repository.DistrictZoneMappingRepository;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.domain.repository.TariffRuleRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import com.swiftdrop.pricing.exception.UnsupportedPricingSpeedException;
import com.swiftdrop.pricing.service.strategy.PricingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPricingEngine {

    private static final String DEFAULT_ROUTE = "ALL";

    private final DistrictZoneMappingRepository districtZoneMappingRepository;
    private final PricingZoneRepository pricingZoneRepository;
    private final List<PricingStrategy> pricingStrategies;
    private final TariffRuleRepository tariffRuleRepository;

    public PricingQuoteResponse calculateQuote(PricingQuoteRequest request) {
        log.info("Calculating delivery quote from {} to {}, weight={}kg, speed={}",
                request.pickupDistrict(), request.deliveryDistrict(),
                request.actualWeightKg(), request.speed());

        TariffRule rule = tariffRuleRepository.findActiveRules(DEFAULT_ROUTE, DEFAULT_ROUTE).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active default tariff rule is configured"));

        // 1. Resolve zones
        String pickupZoneCode = districtZoneMappingRepository.findByDistrict(request.pickupDistrict().name())
                .map(DistrictZoneMapping::getZoneCode)
                .orElse("ZONE_WESTERN");

        String deliveryZoneCode = districtZoneMappingRepository.findByDistrict(request.deliveryDistrict().name())
                .map(DistrictZoneMapping::getZoneCode)
                .orElse("ZONE_WESTERN");

        PricingZone pickupZone = pricingZoneRepository.findByZoneCode(pickupZoneCode)
                .orElse(null);

        boolean isIntraDistrict = request.pickupDistrict() == request.deliveryDistrict();

        // 2. Volumetric and Chargeable weight calculation
        BigDecimal volumetricWeight = BigDecimal.ZERO;
        if (request.lengthCm() != null && request.widthCm() != null && request.heightCm() != null
                && request.lengthCm().compareTo(BigDecimal.ZERO) > 0
                && request.widthCm().compareTo(BigDecimal.ZERO) > 0
                && request.heightCm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal volume = request.lengthCm().multiply(request.widthCm()).multiply(request.heightCm());
            volumetricWeight = volume.divide(rule.getVolumetricDivisor(), 2, RoundingMode.HALF_UP);
        }

        BigDecimal chargeableWeight = request.actualWeightKg().max(volumetricWeight)
                .setScale(2, RoundingMode.HALF_UP);

        // 3. Base fee & Per-kg surcharge calculation
        BigDecimal baseFee;
        BigDecimal perKgFee;

        if (isIntraDistrict) {
            baseFee = pickupZone != null ? pickupZone.getBaseFee() : new BigDecimal("350.00");
            perKgFee = pickupZone != null ? pickupZone.getPerKgFee() : new BigDecimal("80.00");
        } else {
            // Inter-district fees come from the active default tariff rule
            baseFee = rule.getBasePrice();
            perKgFee = rule.getPricePerStep();
        }

        BigDecimal weightFee = BigDecimal.ZERO;
        if (chargeableWeight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraKg = chargeableWeight.subtract(BigDecimal.ONE)
                    .setScale(0, RoundingMode.CEILING); // standard logistics billing rounds up extra fractional kg
            weightFee = extraKg.multiply(perKgFee).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal standardSubtotal = baseFee.add(weightFee);

        // 4. Service speed pricing strategy
        OrderSpeed speed = request.speed() != null ? request.speed() : OrderSpeed.STANDARD;
        PricingStrategy strategy = pricingStrategies.stream()
                .filter(candidate -> candidate.supports(speed))
                .findFirst()
                .orElseThrow(() -> new UnsupportedPricingSpeedException(speed));

        BigDecimal totalFee = strategy.apply(standardSubtotal, pickupZone).setScale(2, RoundingMode.HALF_UP);
        BigDecimal speedSurcharge = totalFee.subtract(standardSubtotal).setScale(2, RoundingMode.HALF_UP);

        return PricingQuoteResponse.builder()
                .pickupDistrict(request.pickupDistrict())
                .deliveryDistrict(request.deliveryDistrict())
                .pickupZone(pickupZoneCode)
                .deliveryZone(deliveryZoneCode)
                .intraDistrict(isIntraDistrict)
                .actualWeightKg(request.actualWeightKg().setScale(2, RoundingMode.HALF_UP))
                .volumetricWeightKg(volumetricWeight)
                .chargeableWeightKg(chargeableWeight)
                .speed(speed)
                .baseFee(baseFee)
                .weightFee(weightFee)
                .speedSurcharge(speedSurcharge)
                .estimatedFee(totalFee)
                .currency("LKR")
                .pricingRuleVersionId(rule.getRuleVersionId())
                .build();
    }
}