package com.swiftdrop.pricing;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.pricing.domain.entity.TariffRule;
import com.swiftdrop.pricing.domain.repository.TariffRuleRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import com.swiftdrop.pricing.service.DynamicPricingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class EstimateEndpointIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DynamicPricingEngine engine;

    @Autowired
    private TariffRuleRepository tariffRuleRepository;

    private PricingQuoteRequest request(SriLankanDistrict from, SriLankanDistrict to, String kg) {
        return PricingQuoteRequest.builder()
                .pickupDistrict(from)
                .deliveryDistrict(to)
                .actualWeightKg(new BigDecimal(kg))
                .speed(OrderSpeed.STANDARD)
                .build();
    }

    @Test
    @DisplayName("Same district uses the seeded Western zone fees")
    void sameDistrictUsesZoneFees() {
        PricingQuoteResponse response = engine.calculateQuote(
                request(SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, "50.00"));

        // 350 + 49 x 80 = 4270
        assertThat(response.estimatedFee()).isEqualByComparingTo("4270.00");
    }

    @Test
    @DisplayName("Different districts in the same zone use the zone fees")
    void sameZoneDifferentDistrictsUseZoneFees() {
        PricingQuoteResponse response = engine.calculateQuote(
                request(SriLankanDistrict.COLOMBO, SriLankanDistrict.KALUTARA, "50.00"));

        assertThat(response.pickupZone()).isEqualTo("ZONE_WESTERN");
        assertThat(response.deliveryZone()).isEqualTo("ZONE_WESTERN");
        assertThat(response.estimatedFee()).isEqualByComparingTo("4270.00");
    }

    @Test
    @DisplayName("Different zones use the default tariff")
    void differentZonesUseDefaultTariff() {
        TariffRule rule = tariffRuleRepository.findActiveRules("ALL", "ALL").get(0);

        PricingQuoteResponse response = engine.calculateQuote(
                request(SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, "50.00"));

        BigDecimal expected = rule.getBasePrice()
                .add(rule.getPricePerStep().multiply(new BigDecimal("49")));
        assertThat(response.baseFee()).isEqualByComparingTo(rule.getBasePrice());
        assertThat(response.estimatedFee()).isEqualByComparingTo(expected);
    }
}
