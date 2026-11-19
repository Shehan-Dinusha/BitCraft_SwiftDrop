package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.domain.entity.MerchantOrderReceipt;
import com.swiftdrop.merchant.domain.repository.MerchantOrderReceiptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantOrderQueryController.class)
class MerchantOrderQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MerchantOrderReceiptRepository receipts;
    final UUID merchant = UUID.randomUUID();

    @Test void unauthenticatedAndForeignMerchantCannotReadReceipts() throws Exception {
        String path = "/api/v1/merchants/" + merchant + "/orders";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-User-Role", "MERCHANT")
                .header("X-Merchant-Id", UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(get(path).header("X-User-Role", "CUSTOMER")
                .header("X-Merchant-Id", merchant)).andExpect(status().isForbidden());
        verifyNoInteractions(receipts);
    }

    @Test void ownerReceivesReceiptAndPaginationIsBounded() throws Exception {
        var receipt = MerchantOrderReceipt.builder().merchantId(merchant).externalRef("SHOP-1")
                .orderId(UUID.randomUUID()).trackingNumber("SD-TEST-0001").createdAt(Instant.now()).build();
        when(receipts.findByMerchantIdOrderByCreatedAtDesc(merchant, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(receipt)));
        mvc.perform(get("/api/v1/merchants/" + merchant + "/orders?page=-1&size=1000")
                .header("X-User-Role", "MERCHANT").header("X-Merchant-Id", merchant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].externalRef").value("SHOP-1"))
                .andExpect(jsonPath("$.data[0].trackingNumber").value("SD-TEST-0001"));
        verify(receipts).findByMerchantIdOrderByCreatedAtDesc(merchant, PageRequest.of(0, 100));
    }
}
