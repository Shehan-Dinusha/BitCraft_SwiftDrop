package com.swiftdrop.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.dto.CreateMerchantRequest;
import com.swiftdrop.merchant.dto.MerchantAccountResponse;
import com.swiftdrop.merchant.service.MerchantAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantAccountController.class)
class MerchantAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MerchantAccountService merchantAccountService;

    private final UUID merchantId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/merchants/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/merchants should return 201 Created")
    void testCreateMerchant() throws Exception {
        CreateMerchantRequest request = new CreateMerchantRequest(
                "Lanka E-Commerce Hub",
                "info@lankahub.lk",
                "+94 11 999 8888",
                "10 Union Place, Colombo 02",
                SriLankanDistrict.COLOMBO
        );

        MerchantAccountResponse res = new MerchantAccountResponse(
                merchantId,
                "Lanka E-Commerce Hub",
                "info@lankahub.lk",
                "+94 11 999 8888",
                "10 Union Place, Colombo 02",
                SriLankanDistrict.COLOMBO,
                "ACTIVE",
                Instant.now()
        );

        when(merchantAccountService.createMerchant(any(CreateMerchantRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.businessName").value("Lanka E-Commerce Hub"));
    }

    @Test
    @DisplayName("GET /api/v1/merchants/{merchantId} should return 200 OK")
    void testGetMerchant() throws Exception {
        MerchantAccountResponse res = new MerchantAccountResponse(
                merchantId,
                "Lanka E-Commerce Hub",
                "info@lankahub.lk",
                "+94 11 999 8888",
                "10 Union Place, Colombo 02",
                SriLankanDistrict.COLOMBO,
                "ACTIVE",
                Instant.now()
        );

        when(merchantAccountService.getOrCreateMerchant(eq(merchantId), any())).thenReturn(res);

        mockMvc.perform(get("/api/v1/merchants/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.merchantId").value(merchantId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/merchants should return list")
    void testListMerchants() throws Exception {
        MerchantAccountResponse res = new MerchantAccountResponse(
                merchantId,
                "Store A",
                "storea@test.lk",
                null,
                null,
                SriLankanDistrict.COLOMBO,
                "ACTIVE",
                Instant.now()
        );

        when(merchantAccountService.getAllMerchants()).thenReturn(List.of(res));

        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].businessName").value("Store A"));
    }
}
