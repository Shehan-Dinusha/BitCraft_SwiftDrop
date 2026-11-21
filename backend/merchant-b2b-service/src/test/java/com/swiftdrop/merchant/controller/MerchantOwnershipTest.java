package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.dto.BulkIngestBatchResponse;
import com.swiftdrop.merchant.dto.MerchantAccountResponse;
import com.swiftdrop.merchant.service.BulkCsvOrderProcessor;
import com.swiftdrop.merchant.service.BulkOrderIngestionService;
import com.swiftdrop.merchant.service.MerchantAccountService;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import com.swiftdrop.merchant.service.MerchantApprovalGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A signed-in user may act only on their own merchant company: before this check, any caller could
 * create API keys for (and so send orders as) any approved merchant, or read another merchant's
 * upload batches with recipient addresses.
 */
@WebMvcTest({ApiKeyManagementController.class, MerchantOrderIngestController.class, MerchantAccountController.class})
class MerchantOwnershipTest {

    @Autowired MockMvc mvc;
    @MockitoBean MerchantApiKeyService apiKeyService;
    @MockitoBean BulkOrderIngestionService ingestionService;
    @MockitoBean BulkCsvOrderProcessor csvProcessor;
    @MockitoBean MerchantApprovalGuard approvalGuard;
    @MockitoBean MerchantAccountService merchantAccountService;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID otherMerchant = UUID.randomUUID();

    @Test
    @DisplayName("Another merchant or a customer cannot create, list or revoke this merchant's API keys")
    void apiKeysAreOwnerOnly() throws Exception {
        String body = "{\"keyName\":\"Stolen\",\"expiresInDays\":30}";
        mvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys").with(TestCallers.merchant(otherMerchant))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys").with(TestCallers.customer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/" + merchantId + "/api-keys").with(TestCallers.admin()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/merchants/" + merchantId + "/api-keys/" + UUID.randomUUID())
                .with(TestCallers.merchant(otherMerchant))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys/" + UUID.randomUUID() + "/regenerate"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    @DisplayName("Another merchant cannot upload orders or read this merchant's batches")
    void ingestionAndBatchesAreOwnerOnly() throws Exception {
        mvc.perform(post("/api/v1/merchants/" + merchantId + "/ingest/bulk").with(TestCallers.merchant(otherMerchant))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"fileName":"x","orders":[{"recipientName":"A","recipientPhone":"+94771234567",
                         "deliveryDistrict":"COLOMBO","deliveryAddress":"1 Main St","packageWeightKg":1}]}
                        """))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/" + merchantId + "/batches").with(TestCallers.merchant(otherMerchant)))
                .andExpect(status().isForbidden());
        verify(ingestionService, never()).processBulkIngest(any(), any(), any());
        verify(ingestionService, never()).listBatches(any());
    }

    @Test
    @DisplayName("A batch and its rows (recipient addresses) are shown only to the merchant that uploaded it")
    void batchRowsAreOwnerOnly() throws Exception {
        UUID batchId = UUID.randomUUID();
        BulkIngestBatchResponse batch = mock(BulkIngestBatchResponse.class);
        when(batch.merchantId()).thenReturn(merchantId);
        when(ingestionService.getBatchById(batchId)).thenReturn(batch);

        mvc.perform(get("/api/v1/merchants/batches/" + batchId + "/items").with(TestCallers.merchant(otherMerchant)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/batches/" + batchId).with(TestCallers.merchant(otherMerchant)))
                .andExpect(status().isForbidden());
        verify(ingestionService, never()).getBatchItems(any());

        mvc.perform(get("/api/v1/merchants/batches/" + batchId + "/items").with(TestCallers.merchant(merchantId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A merchant profile is visible to its own users, its district dispatcher and admins only")
    void profileVisibility() throws Exception {
        MerchantAccountResponse profile = mock(MerchantAccountResponse.class);
        when(profile.operatingDistrict()).thenReturn(SriLankanDistrict.KANDY);
        when(merchantAccountService.getMerchantById(merchantId)).thenReturn(profile);

        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestCallers.merchant(otherMerchant)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestCallers.customer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestCallers.dispatcher(SriLankanDistrict.GALLE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestCallers.dispatcher(SriLankanDistrict.KANDY)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestCallers.merchant(merchantId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Only an admin can list every merchant or provision one directly")
    void listingAndProvisioningAreAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/merchants").with(TestCallers.merchant(merchantId))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants").with(TestCallers.dispatcher(SriLankanDistrict.KANDY)))
                .andExpect(status().isForbidden());
        verify(merchantAccountService, never()).getAllMerchants();
    }
}
