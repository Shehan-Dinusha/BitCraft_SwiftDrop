package com.swiftdrop.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.B2bTestData;
import com.swiftdrop.merchant.dto.*;
import com.swiftdrop.merchant.service.B2bOrderSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B2bOrderController.class)
class ApiKeyHeaderAuthTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean B2bOrderSubmissionService service;
    UUID merchant = UUID.randomUUID();

    @Test void rawOrMissingApiKeyCannotReplaceTrustedGatewayIdentity() throws Exception {
        for (String key : new String[]{"", "invalid"}) {
            mvc.perform(post("/api/v1/b2b/orders").header("X-API-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(B2bTestData.order())))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test void trustedMerchantGetsCreatedThenExistingOrder() throws Exception {
        UUID order = UUID.randomUUID();
        when(service.submit(eq(merchant), any(), any())).thenReturn(
                new B2bOrderResult("SHOP-001", order, "SD-ABCD-EFGH", true, null),
                new B2bOrderResult("SHOP-001", order, "SD-ABCD-EFGH", false, null));
        var body = json.writeValueAsString(B2bTestData.order());
        mvc.perform(post("/api/v1/b2b/orders").header("X-Merchant-Id", merchant).header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/b2b/orders").header("X-Merchant-Id", merchant).header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test void bulkHasPerItemErrorsAndRejectsOverLimit() throws Exception {
        when(service.submit(eq(merchant), any(), any())).thenReturn(
                new B2bOrderResult("SHOP-001", UUID.randomUUID(), "SD-ABCD-EFGH", true, null))
                .thenThrow(new IllegalArgumentException("Invalid phone"));
        mvc.perform(post("/api/v1/b2b/orders/bulk").header("X-Merchant-Id", merchant).header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        new B2bBulkRequest(List.of(B2bTestData.order(), B2bTestData.order())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1)).andExpect(jsonPath("$.data.items[1].error").value("Invalid phone"));
        mvc.perform(post("/api/v1/b2b/orders/bulk").header("X-Merchant-Id", merchant).header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        new B2bBulkRequest(Collections.nCopies(501, B2bTestData.order())))))
                .andExpect(status().isBadRequest());
    }
}
