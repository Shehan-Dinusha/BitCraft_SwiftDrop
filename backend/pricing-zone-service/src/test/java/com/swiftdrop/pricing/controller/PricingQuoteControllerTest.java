package com.swiftdrop.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import com.swiftdrop.pricing.service.DynamicPricingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PricingQuoteController.class)
class PricingQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DynamicPricingEngine dynamicPricingEngine;

    @MockitoBean
    private PricingZoneRepository pricingZoneRepository;

    @Test
    @DisplayName("GET /api/v1/pricing/health returns 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/pricing/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pricing service is online"));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/quote returns calculated quote")
    void testCalculateQuote() throws Exception {
        PricingQuoteResponse mockResponse = PricingQuoteResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.GAMPAHA)
                .estimatedFee(new BigDecimal("350.00"))
                .currency("LKR")
                .pricingRuleVersionId("PRICING-2026-V1")
                .build();

        when(dynamicPricingEngine.calculateQuote(any(PricingQuoteRequest.class)))
                .thenReturn(mockResponse);

        PricingQuoteRequest request = PricingQuoteRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.GAMPAHA)
                .actualWeightKg(new BigDecimal("1.2"))
                .speed(OrderSpeed.STANDARD)
                .build();

        mockMvc.perform(post("/api/v1/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.estimatedFee").value(350.00))
                .andExpect(jsonPath("$.data.currency").value("LKR"))
                .andExpect(jsonPath("$.data.pricingRuleVersionId").value("PRICING-2026-V1"));
    }

    @Test
    @DisplayName("GET /api/v1/pricing/zones returns list of zones")
    void testListZones() throws Exception {
        PricingZone zone = PricingZone.builder()
                .id(1L)
                .zoneCode("ZONE_WESTERN")
                .zoneName("Western Province Metro")
                .tier("METRO")
                .baseFee(new BigDecimal("350.00"))
                .build();

        when(pricingZoneRepository.findAll()).thenReturn(List.of(zone));

        mockMvc.perform(get("/api/v1/pricing/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].zoneCode").value("ZONE_WESTERN"));
    }
}
