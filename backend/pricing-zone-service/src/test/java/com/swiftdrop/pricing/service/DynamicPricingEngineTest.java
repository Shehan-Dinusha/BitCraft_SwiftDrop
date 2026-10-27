package com.swiftdrop.pricing.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.repository.DistrictZoneMappingRepository;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicPricingEngineTest {

    @Mock
    private DistrictZoneMappingRepository districtZoneMappingRepository;

    @Mock
    private PricingZoneRepository pricingZoneRepository;

    @InjectMocks
    private DynamicPricingEngine pricingEngine;

    private PricingZone westernZone;

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

        assertThat(response.isIntraDistrict()).isTrue();
        assertThat(response.getChargeableWeightKg()).isEqualByComparingTo("0.80");
        assertThat(response.getBaseFee()).isEqualByComparingTo("350.00");
        assertThat(response.getWeightFee()).isEqualByComparingTo("0.00");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("350.00");
        assertThat(response.getPricingRuleVersionId()).isEqualTo(DynamicPricingEngine.CURRENT_RULE_VERSION);
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

        assertThat(response.getVolumetricWeightKg()).isEqualByComparingTo("12.00");
        assertThat(response.getChargeableWeightKg()).isEqualByComparingTo("12.00");
        // Extra kg = 12 - 1 = 11 kg * 80.00 = 880.00. Base = 350.00. Total = 1230.00
        assertThat(response.getWeightFee()).isEqualByComparingTo("880.00");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("1230.00");
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

        assertThat(response.isIntraDistrict()).isFalse();
        assertThat(response.getBaseFee()).isEqualByComparingTo("550.00");
        assertThat(response.getWeightFee()).isEqualByComparingTo("240.00");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("1185.00");
        assertThat(response.getSpeedSurcharge()).isEqualByComparingTo("395.00");
    }
}
