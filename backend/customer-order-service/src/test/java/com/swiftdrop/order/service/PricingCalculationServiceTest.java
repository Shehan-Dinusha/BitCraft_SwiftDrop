package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculationServiceTest {

    private final PricingCalculationService pricingService = new PricingCalculationService();

    @Test
    @DisplayName("Pre-confirmation estimate for intra-district standard package under 1kg charges base rate")
    void testIntraDistrictBaseRate() {
        FeeEstimateRequest request = FeeEstimateRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.COLOMBO)
                .actualWeightKg(new BigDecimal("0.75"))
                .lengthCm(new BigDecimal("10.0"))
                .widthCm(new BigDecimal("10.0"))
                .heightCm(new BigDecimal("10.0"))
                .speed(OrderSpeed.STANDARD)
                .build();

        FeeEstimateResponse response = pricingService.calculateEstimate(request);

        assertThat(response.isIntraDistrict()).isTrue();
        assertThat(response.getBaseFee()).isEqualByComparingTo("350.00");
        assertThat(response.getWeightFee()).isEqualByComparingTo("0.00");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("350.00");
        assertThat(response.getCurrency()).isEqualTo("LKR");
        assertThat(response.getPricingRuleVersionId()).isEqualTo(PricingCalculationService.CURRENT_RULE_VERSION);
    }

    @Test
    @DisplayName("Inter-district express package with extra weight applies formula and 1.5x multiplier")
    void testInterDistrictExpressExtraWeight() {
        // Inter-district: base 550.00.
        // Volumetric: (30 * 20 * 15) / 5000 = 9000 / 5000 = 1.80 kg. Actual = 2.40 kg -> Chargeable = 2.40 kg
        // Extra kg: ceil(2.40 - 1.0) = 2 kg * 120.00 = 240.00. Subtotal = 550 + 240 = 790.00
        // Express 1.5x: 790 * 1.50 = 1185.00
        FeeEstimateRequest request = FeeEstimateRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.GALLE)
                .actualWeightKg(new BigDecimal("2.40"))
                .lengthCm(new BigDecimal("30.0"))
                .widthCm(new BigDecimal("20.0"))
                .heightCm(new BigDecimal("15.0"))
                .speed(OrderSpeed.EXPRESS)
                .build();

        FeeEstimateResponse response = pricingService.calculateEstimate(request);

        assertThat(response.isIntraDistrict()).isFalse();
        assertThat(response.getChargeableWeightKg()).isEqualByComparingTo("2.40");
        assertThat(response.getBaseFee()).isEqualByComparingTo("550.00");
        assertThat(response.getWeightFee()).isEqualByComparingTo("240.00");
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("1185.00");
        assertThat(response.getSpeedSurcharge()).isEqualByComparingTo("395.00");
    }
}
