package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.domain.entity.*;
import com.swiftdrop.merchant.domain.repository.*;
import com.swiftdrop.merchant.messaging.publisher.MerchantEventPublisher;
import com.swiftdrop.merchant.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ApiKeyManagementController.class, MerchantOrderIngestController.class})
@Import({MerchantApiKeyService.class, BulkOrderIngestionService.class, MerchantApprovalGuard.class})
class PendingMerchantCannotOrderTest {
    @Autowired MockMvc mvc;
    @MockitoBean MerchantAccountRepository accounts;
    @MockitoBean MerchantApiKeyRepository keys;
    @MockitoBean BulkIngestBatchRepository batches;
    @MockitoBean BulkBatchItemRepository items;
    @MockitoBean BulkCsvOrderProcessor csv;
    @MockitoBean MerchantEventPublisher events;
    @MockitoBean B2bOrderSubmissionService submissions;
    UUID id = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void unapprovedMerchantsCannotGenerateKeysOrIngestOrders(MerchantStatus state) throws Exception {
        when(accounts.findByMerchantId(id)).thenReturn(Optional.of(
                MerchantAccount.builder().merchantId(id).status(state).build()));
        mvc.perform(post("/api/v1/merchants/" + id + "/api-keys")
                .contentType(MediaType.APPLICATION_JSON).content("{\"keyName\":\"ERP\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/merchants/" + id + "/ingest/bulk")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"orders":[{"recipientName":"Test","recipientPhone":"+94771234567",
                    "deliveryDistrict":"COLOMBO","deliveryAddress":"Colombo"}]}
                    """))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/merchants/" + id + "/ingest/csv")
                .file(new MockMultipartFile("file", "orders.csv", "text/csv", "header\nrow".getBytes())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(keys, batches, items, csv, events, submissions);
    }

    @Test void approvedMerchantCanGenerateKey() throws Exception {
        when(accounts.findByMerchantId(id)).thenReturn(Optional.of(
                MerchantAccount.builder().merchantId(id).status(MerchantStatus.APPROVED).build()));
        when(keys.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/api/v1/merchants/" + id + "/api-keys")
                .contentType(MediaType.APPLICATION_JSON).content("{\"keyName\":\"ERP\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.rawApiKey").isNotEmpty());
    }
}
