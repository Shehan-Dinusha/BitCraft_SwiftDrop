package com.swiftdrop.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.dto.BulkIngestBatchResponse;
import com.swiftdrop.merchant.dto.BulkIngestRequest;
import com.swiftdrop.merchant.dto.BulkOrderIngestItem;
import com.swiftdrop.merchant.service.BulkCsvOrderProcessor;
import com.swiftdrop.merchant.service.BulkOrderIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantOrderIngestController.class)
class MerchantOrderIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BulkOrderIngestionService ingestionService;

    @MockitoBean
    private BulkCsvOrderProcessor csvProcessor;

    private final UUID merchantId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/merchant-order-ingest/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/merchant-order-ingest/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/{merchantId}/ingest/bulk should return 202 Accepted")
    void testIngestBulkOrders() throws Exception {
        BulkOrderIngestItem item = new BulkOrderIngestItem(
                "Saman Perera",
                "+94 77 123 4567",
                SriLankanDistrict.COLOMBO,
                "123 Baseline Rd",
                BigDecimal.valueOf(1.0),
                "STANDARD"
        );
        BulkIngestRequest req = new BulkIngestRequest("test.csv", List.of(item));

        UUID batchId = UUID.randomUUID();
        BulkIngestBatchResponse res = new BulkIngestBatchResponse(
                batchId,
                merchantId,
                "test.csv",
                "COMPLETED",
                1,
                1,
                0,
                null,
                Instant.now(),
                Instant.now()
        );

        when(ingestionService.processBulkIngest(eq(merchantId), any(BulkIngestRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/ingest/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.batchStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRecords").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/{merchantId}/ingest/csv should process multipart file")
    void testIngestCsvOrders() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "orders.csv",
                "text/csv",
                "recipient_name,recipient_phone,delivery_district,delivery_address\nKasun,+94770000000,COLOMBO,Galle Rd".getBytes()
        );

        BulkOrderIngestItem item = new BulkOrderIngestItem(
                "Kasun",
                "+94770000000",
                SriLankanDistrict.COLOMBO,
                "Galle Rd",
                BigDecimal.valueOf(1.0),
                "STANDARD"
        );

        when(csvProcessor.parseCsv(any(InputStream.class))).thenReturn(List.of(item));

        UUID batchId = UUID.randomUUID();
        BulkIngestBatchResponse res = new BulkIngestBatchResponse(
                batchId,
                merchantId,
                "orders.csv",
                "COMPLETED",
                1,
                1,
                0,
                null,
                Instant.now(),
                Instant.now()
        );

        when(ingestionService.processBulkIngest(eq(merchantId), any(BulkIngestRequest.class))).thenReturn(res);

        mockMvc.perform(multipart("/api/v1/merchants/" + merchantId + "/ingest/csv")
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.batchId").value(batchId.toString()));
    }
}
