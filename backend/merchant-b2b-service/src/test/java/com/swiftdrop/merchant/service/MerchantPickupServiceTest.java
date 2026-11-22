package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantOrderReceipt;
import com.swiftdrop.merchant.domain.repository.MerchantOrderReceiptRepository;
import com.swiftdrop.merchant.dto.MerchantPickupResponse;
import com.swiftdrop.merchant.dto.MerchantPickupState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantPickupServiceTest {

    private final MerchantOrderReceiptRepository receipts = mock(MerchantOrderReceiptRepository.class);
    private final MerchantApprovalGuard guard = mock(MerchantApprovalGuard.class);
    private final DispatchPickupClient dispatch = mock(DispatchPickupClient.class);
    private final MerchantPickupService service = new MerchantPickupService(receipts, guard, dispatch);
    private final UUID merchant = UUID.randomUUID();

    @Test
    @DisplayName("Adds up the cash to prepare per state, and says which parcels have no known fee")
    void totalsPerState() {
        var a = parcel(MerchantPickupState.READY);
        var b = parcel(MerchantPickupState.READY);
        var c = parcel(MerchantPickupState.WAITING);
        var old = parcel(MerchantPickupState.ASSIGNED);
        when(dispatch.atShop(merchant)).thenReturn(List.of(a, b, c, old));
        when(receipts.findByMerchantIdAndOrderIdIn(eq(merchant), any()))
                .thenReturn(List.of(receipt(a, "350.00"), receipt(b, "550.00"), receipt(c, "350.00"), receipt(old, null)));

        MerchantPickupResponse pickup = service.current(merchant);

        assertThat(pickup.ready().parcels()).isEqualTo(2);
        assertThat(pickup.ready().total()).isEqualByComparingTo("900");
        assertThat(pickup.waiting().total()).isEqualByComparingTo("350");
        assertThat(pickup.assigned().total()).isEqualByComparingTo("0");
        assertThat(pickup.assigned().withoutFee()).isEqualTo(1);
        assertThat(pickup.parcels()).hasSize(4);
    }

    @Test
    @DisplayName("Ready sends the shop's own name, and is refused when there is nothing to pick up")
    void ready() {
        MerchantAccount account = mock(MerchantAccount.class);
        when(account.getBusinessName()).thenReturn("Nimal Fashion");
        when(guard.requireApproved(merchant)).thenReturn(account);
        when(dispatch.markReady(merchant, "Nimal Fashion")).thenReturn(List.of());

        assertThatThrownBy(() -> service.ready(merchant)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("A merchant who is not approved cannot ask for a pickup")
    void notApproved() {
        when(guard.requireApproved(merchant)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.ready(merchant)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(dispatch);
    }

    private static DispatchPickupClient.Parcel parcel(MerchantPickupState state) {
        return new DispatchPickupClient.Parcel(UUID.randomUUID(), "SD-TEST", state, null, null);
    }

    private MerchantOrderReceipt receipt(DispatchPickupClient.Parcel parcel, String fee) {
        return MerchantOrderReceipt.builder().merchantId(merchant).externalRef("SHOP-1").orderId(parcel.orderId())
                .trackingNumber(parcel.trackingNumber()).fee(fee == null ? null : new BigDecimal(fee)).build();
    }
}
