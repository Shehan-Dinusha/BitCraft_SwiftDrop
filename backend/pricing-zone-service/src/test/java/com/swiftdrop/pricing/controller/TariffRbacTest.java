package com.swiftdrop.pricing.controller;

import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.pricing.security.AdminAccessGuard;
import com.swiftdrop.pricing.service.TariffAdminService;
import com.swiftdrop.pricing.service.ZoneAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ZoneAdminController.class, TariffAdminController.class})
@Import(AdminAccessGuard.class)
class TariffRbacTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";

    private static final String TARIFF_BODY = """
            {"basePrice": 550.00, "weightStepKg": 1.00, "pricePerStep": 120.00, "volumetricDivisor": 5000.00}
            """;

    private static final String ZONE_BODY = """
            {"zoneCode": "ZONE_X", "zoneName": "Test Zone", "tier": "URBAN", "baseFee": 400.00,
             "perKgFee": 90.00, "expressMultiplier": 1.50, "sameDayMultiplier": 2.00}
            """;

    private static final String MAPPING_BODY = "{\"zoneCode\": \"ZONE_X\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZoneAdminService zoneAdminService;

    @MockitoBean
    private TariffAdminService tariffAdminService;

    private MockHttpServletRequestBuilder withRole(MockHttpServletRequestBuilder builder, String role, String body) {
        return builder
                .header(JwtHeaderConstants.X_USER_ID, USER_ID)
                .header(JwtHeaderConstants.X_USER_ROLE, role)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "COURIER", "MERCHANT"})
    void tariffPublish_isForbiddenForNonAdminRoles(String role) throws Exception {
        mockMvc.perform(withRole(put("/api/v1/pricing/tariffs/default"), role, TARIFF_BODY))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISPATCHER", "SYSTEM_ADMIN"})
    void tariffPublish_isAllowedForAdminRoles(String role) throws Exception {
        mockMvc.perform(withRole(put("/api/v1/pricing/tariffs/default"), role, TARIFF_BODY))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "COURIER", "MERCHANT"})
    void zoneWrites_areForbiddenForNonAdminRoles(String role) throws Exception {
        mockMvc.perform(withRole(post("/api/v1/zones"), role, ZONE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(withRole(put("/api/v1/zones/ZONE_X"), role, ZONE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(withRole(delete("/api/v1/zones/ZONE_X"), role, ""))
                .andExpect(status().isForbidden());
        mockMvc.perform(withRole(put("/api/v1/zones/districts/KANDY"), role, MAPPING_BODY))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISPATCHER", "SYSTEM_ADMIN"})
    void zoneWrites_areAllowedForAdminRoles(String role) throws Exception {
        mockMvc.perform(withRole(post("/api/v1/zones"), role, ZONE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(withRole(put("/api/v1/zones/ZONE_X"), role, ZONE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(withRole(delete("/api/v1/zones/ZONE_X"), role, ""))
                .andExpect(status().isOk());
        mockMvc.perform(withRole(put("/api/v1/zones/districts/KANDY"), role, MAPPING_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void writes_withoutIdentityHeaders_areUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/pricing/tariffs/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARIFF_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ZONE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reads_stayOpenForCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/zones")
                        .header(JwtHeaderConstants.X_USER_ID, USER_ID)
                        .header(JwtHeaderConstants.X_USER_ROLE, "CUSTOMER"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pricing/tariffs")
                        .header(JwtHeaderConstants.X_USER_ID, USER_ID)
                        .header(JwtHeaderConstants.X_USER_ROLE, "CUSTOMER"))
                .andExpect(status().isOk());
    }
}
