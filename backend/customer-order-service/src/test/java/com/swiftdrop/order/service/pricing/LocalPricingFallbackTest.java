package com.swiftdrop.order.service.pricing;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.service.PricingCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPricingFallbackTest {

    private final PricingCalculationService calculation = new PricingCalculationService();
    private final PricingClient client = new LocalPricingFallback(calculation);

    private FeeEstimateRequest request(SriLankanDistrict from, SriLankanDistrict to, String weight, OrderSpeed speed) {
        return FeeEstimateRequest.builder()
                .pickupDistrict(from)
                .deliveryDistrict(to)
                .actualWeightKg(new BigDecimal(weight))
                .lengthCm(new BigDecimal("40"))
                .widthCm(new BigDecimal("30"))
                .heightCm(new BigDecimal("30"))
                .speed(speed)
                .build();
    }

    @Test
    @DisplayName("The fallback returns exactly what the existing calculation returns")
    void delegatesWithoutChangingResults() {
        FeeEstimateRequest[] cases = {
                request(SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, "0.5", OrderSpeed.STANDARD),
                request(SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, "2.4", OrderSpeed.EXPRESS),
                request(SriLankanDistrict.KANDY, SriLankanDistrict.GALLE, "12", OrderSpeed.SAME_DAY),
        };

        for (FeeEstimateRequest each : cases) {
            assertThat(client.estimate(each)).isEqualTo(calculation.calculateEstimate(each));
        }
    }

    @Test
    @DisplayName("Exactly one PricingClient bean exists, and it is the local fallback")
    void exactlyOnePricingClientBean() {
        new ApplicationContextRunner()
                .withBean(PricingCalculationService.class)
                .withBean(LocalPricingFallback.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PricingClient.class);
                    assertThat(context.getBean(PricingClient.class)).isInstanceOf(LocalPricingFallback.class);
                });
    }
}
