package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.B2bTestData;
import com.swiftdrop.merchant.domain.entity.*;
import com.swiftdrop.merchant.domain.repository.*;
import com.swiftdrop.merchant.dto.B2bOrderResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class B2bOrderForwardingTest {
    @Mock MerchantAccountRepository merchants;
    @Mock MerchantOrderReceiptRepository receipts;
    @Mock OrderServiceClient orders;
    UUID merchant = UUID.randomUUID();
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    B2bOrderSubmissionService service;
    @BeforeEach void setup() {
        service = new B2bOrderSubmissionService(merchants, receipts,
                new MerchantOrderNormalizer(FACTORY.getValidator()), orders);
    }
    @AfterAll static void close() { FACTORY.close(); }

    @Test void forwardsOnceAndReturnsPersistedIdentityOnRetry() {
        when(merchants.findForUpdate(merchant)).thenReturn(Optional.of(
                MerchantAccount.builder().status(MerchantStatus.APPROVED).build()));
        UUID orderId = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        when(orders.create(any(), eq(correlation))).thenReturn(new B2bOrderResult("SHOP-001", orderId, "SD-ABCD-EFGH", true, null));
        var receipt = MerchantOrderReceipt.builder().orderId(orderId).trackingNumber("SD-ABCD-EFGH").build();
        when(receipts.findByMerchantIdAndExternalRef(merchant, "SHOP-001"))
                .thenReturn(Optional.empty(), Optional.of(receipt));
        assertThat(service.submit(merchant, B2bTestData.order(), correlation).created()).isTrue();
        var duplicate = service.submit(merchant, B2bTestData.order(), correlation);
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.orderId()).isEqualTo(orderId);
        verify(orders, times(1)).create(any(), eq(correlation));
        verify(receipts, times(1)).save(any());
    }

    @Test void unapprovedMerchantCannotReachOrderService() {
        when(merchants.findForUpdate(merchant)).thenReturn(Optional.of(
                MerchantAccount.builder().status(MerchantStatus.PENDING_APPROVAL).build()));
        assertThatThrownBy(() -> service.submit(merchant, B2bTestData.order(), null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(orders, receipts);
    }

    @Test void downstreamFailureDoesNotStoreSuccessReceipt() {
        when(merchants.findForUpdate(merchant)).thenReturn(Optional.of(
                MerchantAccount.builder().status(MerchantStatus.APPROVED).build()));
        when(orders.create(any(), any())).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Order service unavailable"));
        assertThatThrownBy(() -> service.submit(merchant, B2bTestData.order(), null)).isInstanceOf(ResponseStatusException.class);
        verify(receipts, never()).save(any());
    }
}
