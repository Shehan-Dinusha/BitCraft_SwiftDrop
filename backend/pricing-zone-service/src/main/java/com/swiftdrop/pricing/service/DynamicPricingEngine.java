package com.swiftdrop.pricing.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
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
        String pickupZoneCode = resolveZoneCode(request.pickupDistrict());
        String deliveryZoneCode = resolveZoneCode(request.deliveryDistrict());

        PricingZone pickupZone = pricingZoneRepository.findByZoneCode(pickupZoneCode)
                .orElseThrow(() -> new IllegalStateException("Pricing zone not found: " + pickupZoneCode));

        boolean isIntraDistrict = request.pickupDistrict() == request.deliveryDistrict();

        // 2. Volumetric and chargeable weight calculation
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

        // 3. Base fee and per-step fee
        BigDecimal baseFee;
        BigDecimal perStepFee;

        if (isIntraDistrict) {
            baseFee = pickupZone.getBaseFee();
            perStepFee = pickupZone.getPerKgFee();
        } else {
            // Inter-district fees come from the active default tariff rule
            baseFee = rule.getBasePrice();
            perStepFee = rule.getPricePerStep();
        }

        // The base fee covers the first weight step; every started extra step is billed
        BigDecimal step = rule.getWeightStepKg();
        BigDecimal weightFee = BigDecimal.ZERO;
        if (chargeableWeight.compareTo(step) > 0) {
            BigDecimal extraSteps = chargeableWeight.subtract(step).divide(step, 0, RoundingMode.CEILING);
            weightFee = extraSteps.multiply(perStepFee).setScale(2, RoundingMode.HALF_UP);
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

    private String resolveZoneCode(SriLankanDistrict district) {
        return districtZoneMappingRepository.findByDistrict(district.name())
                .map(DistrictZoneMapping::getZoneCode)
                .orElseThrow(() -> new IllegalStateException("No pricing zone is mapped for district: " + district));
    }
}