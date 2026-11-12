package com.swiftdrop.pricing.controller;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import com.swiftdrop.pricing.service.DynamicPricingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PricingQuoteController.class)
class PricingEstimateControllerTest {

    private static final String ESTIMATE_URL = "/api/v1/pricing/estimate";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DynamicPricingEngine dynamicPricingEngine;

    @MockitoBean
    private PricingZoneRepository pricingZoneRepository;

    private static String validRequest(String extraFields) {
        return """
                {
                  "pickupDistrict": "COLOMBO",
                  "deliveryDistrict": "KANDY",
                  "actualWeightKg": 2.50,
                  "lengthCm": 20.0,
                  "widthCm": 15.0,
                  "heightCm": 10.0%s
                }
                """.formatted(extraFields);
    }

    private static PricingQuoteResponse quoteResponse(OrderSpeed speed, String fee) {
        return PricingQuoteResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .pickupZone("ZONE_WESTERN")
                .deliveryZone("ZONE_CENTRAL")
                .intraDistrict(false)
                .actualWeightKg(new BigDecimal("2.50"))
                .chargeableWeightKg(new BigDecimal("2.50"))
                .speed(speed)
                .baseFee(new BigDecimal("550.00"))
                .weightFee(new BigDecimal("240.00"))
                .estimatedFee(new BigDecimal(fee))
                .currency("LKR")
                .pricingRuleVersionId("PRICING-2026-V1")
                .build();
    }

    @Test
    @DisplayName("POST /estimate maps the request to the engine and returns the estimate with a strategy")
    void estimateReturnsCalculatedEstimate() throws Exception {
        when(dynamicPricingEngine.calculateQuote(any(PricingQuoteRequest.class)))
                .thenReturn(quoteResponse(OrderSpeed.EXPRESS, "1185.00"));

        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(", \"speed\": \"EXPRESS\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.estimatedFee").value(1185.00))
                .andExpect(jsonPath("$.data.pickupZone").value("ZONE_WESTERN"))
                .andExpect(jsonPath("$.data.deliveryZone").value("ZONE_CENTRAL"))
                .andExpect(jsonPath("$.data.currency").value("LKR"))
                .andExpect(jsonPath("$.data.pricingRuleVersionId").value("PRICING-2026-V1"))
                .andExpect(jsonPath("$.data.strategy").value("EXPRESS"));

        ArgumentCaptor<PricingQuoteRequest> captor = ArgumentCaptor.forClass(PricingQuoteRequest.class);
        verify(dynamicPricingEngine).calculateQuote(captor.capture());
        PricingQuoteRequest sent = captor.getValue();
        assertThat(sent.pickupDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(sent.deliveryDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        assertThat(sent.actualWeightKg()).isEqualByComparingTo("2.50");
        assertThat(sent.speed()).isEqualTo(OrderSpeed.EXPRESS);
    }

    @ParameterizedTest(name = "speed {0} is accepted and reported with strategy {1}")
    @CsvSource({"STANDARD,STANDARD", "EXPRESS,EXPRESS", "SAME_DAY,SAME_DAY"})
    @DisplayName("Every order speed is accepted")
    void everySpeedIsAccepted(OrderSpeed speed, String expectedStrategy) throws Exception {
        when(dynamicPricingEngine.calculateQuote(any(PricingQuoteRequest.class)))
                .thenReturn(quoteResponse(speed, "790.00"));

        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(", \"speed\": \"" + speed.name() + "\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.speed").value(speed.name()))
                .andExpect(jsonPath("$.data.strategy").value(expectedStrategy));
    }

    @Test
    @DisplayName("A missing speed defaults to STANDARD")
    void missingSpeedDefaultsToStandard() throws Exception {
        when(dynamicPricingEngine.calculateQuote(any(PricingQuoteRequest.class)))
                .thenReturn(quoteResponse(OrderSpeed.STANDARD, "790.00"));

        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("STANDARD"));

        ArgumentCaptor<PricingQuoteRequest> captor = ArgumentCaptor.forClass(PricingQuoteRequest.class);
        verify(dynamicPricingEngine).calculateQuote(captor.capture());
        assertThat(captor.getValue().speed()).isEqualTo(OrderSpeed.STANDARD);
    }

    @Test
    @DisplayName("A missing weight returns 400 with a field error and does not call the engine")
    void missingWeightReturnsFieldError() throws Exception {
        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pickupDistrict": "COLOMBO",
                                  "deliveryDistrict": "KANDY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value(ESTIMATE_URL))
                .andExpect(jsonPath("$.fieldErrors.actualWeightKg[0]").value("Actual weight is mandatory"));

        verifyNoInteractions(dynamicPricingEngine);
    }

    @Test
    @DisplayName("A weight below the minimum returns 400 with a field error")
    void tooSmallWeightReturnsFieldError() throws Exception {
        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pickupDistrict": "COLOMBO",
                                  "deliveryDistrict": "KANDY",
                                  "actualWeightKg": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.actualWeightKg[0]")
                        .value("Actual weight must be at least 0.01 kg"));

        verifyNoInteractions(dynamicPricingEngine);
    }

    @Test
    @DisplayName("A missing pickup district returns 400 with a field error")
    void missingPickupDistrictReturnsFieldError() throws Exception {
        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryDistrict": "KANDY",
                                  "actualWeightKg": 1.50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pickupDistrict[0]").value("Pickup district is mandatory"));

        verifyNoInteractions(dynamicPricingEngine);
    }

    @Test
    @DisplayName("An unknown speed value returns 400 with a generic message")
    void unknownSpeedReturnsGenericMessage() throws Exception {
        mockMvc.perform(post(ESTIMATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(", \"speed\": \"TURBO\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"))
                .andExpect(jsonPath("$.path").value(ESTIMATE_URL));

        verifyNoInteractions(dynamicPricingEngine);
    }
}
