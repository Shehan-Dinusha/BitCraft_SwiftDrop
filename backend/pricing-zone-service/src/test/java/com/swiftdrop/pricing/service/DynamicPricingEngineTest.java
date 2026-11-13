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
import com.swiftdrop.pricing.service.strategy.ExpressPricingStrategy;
import com.swiftdrop.pricing.service.strategy.SameDayPricingStrategy;
import com.swiftdrop.pricing.service.strategy.StandardPricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicPricingEngineTest {

    @Mock
    private DistrictZoneMappingRepository districtZoneMappingRepository;

    @Mock
    private PricingZoneRepository pricingZoneRepository;

    @Mock
    private TariffRuleRepository tariffRuleRepository;

    private DynamicPricingEngine pricingEngine;

    private PricingZone westernZone;

    private TariffRule defaultRule;

    @BeforeEach
    void setUp() {
        westernZone = PricingZone.builder()
                .zoneCode("ZONE_WESTERN")
                .zoneName("Western Province Metro")
                .tier("METRO")
                .baseFee(new BigDecimal("350.00"))
                .perKgFee(new BigDecimal("80.00"))
                .expressMultiplier(new BigDecimal("1.50"))
                .sameDayMultiplier(new BigDecimal("2.00"))
                .status("ACTIVE")
                .build();

        defaultRule = TariffRule.builder()
                .ruleVersionId("PRICING-2026-V1")
                .fromDistrict("ALL")
                .toDistrict("ALL")
                .basePrice(new BigDecimal("550.00"))
                .weightStepKg(BigDecimal.ONE)
                .pricePerStep(new BigDecimal("120.00"))
                .volumetricDivisor(new BigDecimal("5000.00"))
                .build();

        lenient().when(tariffRuleRepository.findActiveRules("ALL", "ALL"))
                .thenReturn(List.of(defaultRule));

        pricingEngine = new DynamicPricingEngine(
                districtZoneMappingRepository,
                pricingZoneRepository,
                List.of(new StandardPricingStrategy(),
                        new ExpressPricingStrategy(),
                        new SameDayPricingStrategy()),
                tariffRuleRepository);
    }

    @Test
    @DisplayName("Intra-district standard delivery (under 1 kg) should charge base fee")
    void testIntraDistrictStandardBaseFee() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN"))
                .thenReturn(Optional.of(westernZone));

        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("0.80"))
                .lengthCm(new BigDecimal("15.0"))
                .widthCm(new BigDecimal("10.0"))
                .heightCm(new BigDecimal("5.0"))
                .speed(OrderSpeed.STANDARD)
                .build();

        PricingQuoteResponse response = pricingEngine.calculateQuote(request);

        assertThat(response.intraDistrict()).isTrue();
        assertThat(response.chargeableWeightKg()).isEqualByComparingTo("0.80");
        assertThat(response.baseFee()).isEqualByComparingTo("350.00");
        assertThat(response.weightFee()).isEqualByComparingTo("0.00");
        assertThat(response.estimatedFee()).isEqualByComparingTo("350.00");
        assertThat(response.pricingRuleVersionId()).isEqualTo("PRICING-2026-V1");
    }

    @Test
    @DisplayName("Volumetric weight higher than actual weight determines chargeable weight")
    void testVolumetricWeightDominance() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN"))
                .thenReturn(Optional.of(westernZone));

        // Volume: 50 * 40 * 30 = 60,000 cm3 -> Volumetric = 60,000 / 5,000 = 12.00 kg vs actual 2.0 kg
        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("2.00"))
                .lengthCm(new BigDecimal("50.0"))
                .widthCm(new BigDecimal("40.0"))
                .heightCm(new BigDecimal("30.0"))
                .speed(OrderSpeed.STANDARD)
                .build();

        PricingQuoteResponse response = pricingEngine.calculateQuote(request);

        assertThat(response.volumetricWeightKg()).isEqualByComparingTo("12.00");
        assertThat(response.chargeableWeightKg()).isEqualByComparingTo("12.00");
        // Extra kg = 12 - 1 = 11 kg * 80.00 = 880.00. Base = 350.00. Total = 1230.00
        assertThat(response.weightFee()).isEqualByComparingTo("880.00");
        assertThat(response.estimatedFee()).isEqualByComparingTo("1230.00");
    }

    @Test
    @DisplayName("Inter-district express delivery applies inter-district base and 1.5x multiplier")
    void testInterDistrictExpressMultiplier() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(districtZoneMappingRepository.findByDistrict("KANDY"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("KANDY").zoneCode("ZONE_CENTRAL").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN"))
                .thenReturn(Optional.of(westernZone));

        // Inter-district: base 550.00. Weight 2.5 kg -> extra 2 kg * 120.00 = 240.00. Subtotal = 790.00
        // Express 1.5x -> 790.00 * 1.5 = 1185.00
        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("2.50"))
                .lengthCm(new BigDecimal("20.0"))
                .widthCm(new BigDecimal("15.0"))
                .heightCm(new BigDecimal("10.0"))
                .speed(OrderSpeed.EXPRESS)
                .build();

        PricingQuoteResponse response = pricingEngine.calculateQuote(request);

        assertThat(response.intraDistrict()).isFalse();
        assertThat(response.baseFee()).isEqualByComparingTo("550.00");
        assertThat(response.weightFee()).isEqualByComparingTo("240.00");
        assertThat(response.estimatedFee()).isEqualByComparingTo("1185.00");
        assertThat(response.speedSurcharge()).isEqualByComparingTo("395.00");
    }

    @Test
    @DisplayName("Same-day delivery applies the 2.0x same-day multiplier")
    void testSameDayMultiplier() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN"))
                .thenReturn(Optional.of(westernZone));

        // Base 350.00 for under 1 kg, same-day 2.0x -> 700.00
        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("0.80"))
                .lengthCm(new BigDecimal("15.0"))
                .widthCm(new BigDecimal("10.0"))
                .heightCm(new BigDecimal("5.0"))
                .speed(OrderSpeed.SAME_DAY)
                .build();

        PricingQuoteResponse response = pricingEngine.calculateQuote(request);

        assertThat(response.estimatedFee()).isEqualByComparingTo("700.00");
        assertThat(response.speedSurcharge()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("Speed without a registered strategy throws UnsupportedPricingSpeedException")
    void testUnsupportedSpeedThrowsException() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN"))
                .thenReturn(Optional.of(westernZone));

        DynamicPricingEngine standardOnlyEngine = new DynamicPricingEngine(
                districtZoneMappingRepository,
                pricingZoneRepository,
                List.of(new StandardPricingStrategy()),
                tariffRuleRepository);

        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("0.80"))
                .lengthCm(new BigDecimal("15.0"))
                .widthCm(new BigDecimal("10.0"))
                .heightCm(new BigDecimal("5.0"))
                .speed(OrderSpeed.EXPRESS)
                .build();

        assertThatThrownBy(() -> standardOnlyEngine.calculateQuote(request))
                .isInstanceOf(UnsupportedPricingSpeedException.class);
    }

    @Test
    @DisplayName("Missing default tariff rule fails fast with a clear message")
    void testMissingDefaultTariffRuleThrows() {
        lenient().when(tariffRuleRepository.findActiveRules("ALL", "ALL"))
                .thenReturn(List.of());

        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("1.00"))
                .speed(OrderSpeed.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active default tariff rule");
    }

    @Test
    @DisplayName("District without a zone mapping fails instead of defaulting to the western zone")
    void testMissingZoneMappingThrows() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO")).thenReturn(Optional.empty());

        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("1.00"))
                .speed(OrderSpeed.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pricing zone is mapped");
    }

    @Test
    @DisplayName("Mapped zone code without a zone record fails with a clear message")
    void testMissingZoneRecordThrows() {
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN")).thenReturn(Optional.empty());

        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("1.00"))
                .speed(OrderSpeed.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pricing zone not found");
    }

    @Test
    @DisplayName("Weight step from the tariff rule decides how many extra steps are billed")
    void testWeightStepFromTariffRule() {
        defaultRule.setWeightStepKg(new BigDecimal("2.00"));
        when(districtZoneMappingRepository.findByDistrict("COLOMBO"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("COLOMBO").zoneCode("ZONE_WESTERN").build()));
        when(districtZoneMappingRepository.findByDistrict("KANDY"))
                .thenReturn(Optional.of(DistrictZoneMapping.builder().district("KANDY").zoneCode("ZONE_CENTRAL").build()));
        when(pricingZoneRepository.findByZoneCode("ZONE_WESTERN")).thenReturn(Optional.of(westernZone));

        // 5 kg with 2 kg steps: first step in base fee, (5 - 2) / 2 = 1.5 -> 2 extra steps * 120.00 = 240.00
        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("5.00"))
                .speed(OrderSpeed.STANDARD)
                .build();

        PricingQuoteResponse response = pricingEngine.calculateQuote(request);

        assertThat(response.weightFee()).isEqualByComparingTo("240.00");
        assertThat(response.estimatedFee()).isEqualByComparingTo("790.00");
    }
}