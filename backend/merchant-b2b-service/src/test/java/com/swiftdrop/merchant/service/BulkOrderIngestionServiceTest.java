package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.merchant.domain.entity.BulkBatchItem;
import com.swiftdrop.merchant.domain.entity.BulkIngestBatch;
import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.repository.BulkBatchItemRepository;
import com.swiftdrop.merchant.domain.repository.BulkIngestBatchRepository;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.BulkIngestBatchResponse;
import com.swiftdrop.merchant.dto.BulkIngestRequest;
import com.swiftdrop.merchant.dto.BulkOrderIngestItem;
import com.swiftdrop.merchant.messaging.publisher.MerchantEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkOrderIngestionServiceTest {

    @Mock
    private BulkIngestBatchRepository batchRepository;

    @Mock
    private BulkBatchItemRepository batchItemRepository;

    @Mock
    private MerchantAccountRepository merchantAccountRepository;

    @Mock
    private MerchantEventPublisher eventPublisher;

    private BulkOrderIngestionService ingestionService;

    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ingestionService = new BulkOrderIngestionService(
                batchRepository,
                batchItemRepository,
                merchantAccountRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("processBulkIngest should save batch, create items, and publish OrderCreatedEvents")
    void testProcessBulkIngestSuccess() {
        MerchantAccount merchant = MerchantAccount.builder()
                .merchantId(merchantId)
                .operatingDistrict(SriLankanDistrict.COLOMBO)
                .businessName("Test Store")
                .build();

        when(merchantAccountRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(merchant));
        when(batchRepository.save(any(BulkIngestBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(batchItemRepository.save(any(BulkBatchItem.class))).thenAnswer(i -> i.getArgument(0));

        BulkOrderIngestItem item1 = new BulkOrderIngestItem(
                "Kamal Fernando",
                "+94 77 111 2222",
                SriLankanDistrict.GAMPAHA,
                "12 Negombo Road",
                BigDecimal.valueOf(1.5),
                "STANDARD"
        );
        BulkOrderIngestItem item2 = new BulkOrderIngestItem(
                "Nayani Dias",
                "+94 71 333 4444",
                SriLankanDistrict.KALUTARA,
                "50 Galle Road",
                BigDecimal.valueOf(3.0),
                "EXPRESS"
        );

        BulkIngestRequest request = new BulkIngestRequest("orders.csv", List.of(item1, item2));

        BulkIngestBatchResponse response = ingestionService.processBulkIngest(merchantId, request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.batchStatus());
        assertEquals(2, response.totalRecords());
        assertEquals(2, response.successCount());
        assertEquals(0, response.failedCount());

        verify(eventPublisher, times(2)).publishOrderCreated(any(OrderCreatedEvent.class));
        verify(batchItemRepository, times(2)).save(any(BulkBatchItem.class));
    }

    @Test
    @DisplayName("getBatchById should return batch when present")
    void testGetBatchById() {
        UUID batchId = UUID.randomUUID();
        BulkIngestBatch batch = BulkIngestBatch.builder()
                .batchId(batchId)
                .merchantId(merchantId)
                .fileName("batch.csv")
                .batchStatus("COMPLETED")
                .totalRecords(5)
                .successCount(5)
                .build();

        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));

        BulkIngestBatchResponse res = ingestionService.getBatchById(batchId);

        assertEquals(batchId, res.batchId());
        assertEquals(5, res.totalRecords());
    }
}
