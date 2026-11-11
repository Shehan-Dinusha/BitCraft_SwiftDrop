package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.domain.entity.*;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.merchant.messaging.event.MerchantApprovedEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantApprovalServiceTest {
    @Mock MerchantAccountRepository accounts;
    @Mock ApplicationEventPublisher events;
    MerchantApprovalService service;
    UUID merchantId = UUID.randomUUID(), owner = UUID.randomUUID(), admin = UUID.randomUUID();

    @BeforeEach void setup() { service = new MerchantApprovalService(accounts, events); }

    @Test void registrationSavesOwnerAndPendingStatus() {
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.register(owner, new RegisterMerchantRequest(" Shop ", "BR-1",
                "shop@example.com", "+94771234567", "Colombo", SriLankanDistrict.COLOMBO));
        assertThat(result.ownerUserId()).isEqualTo(owner);
        assertThat(result.businessRegNo()).isEqualTo("BR-1");
        assertThat(result.businessName()).isEqualTo("Shop");
        assertThat(result.status()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        verifyNoInteractions(events);
    }

    @Test void approvalRecordsAdministratorAndCorrelation() {
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(pending()));
        UUID correlation = UUID.randomUUID();
        var result = service.approve(merchantId, admin, correlation);
        assertThat(result.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(result.approvedBy()).isEqualTo(admin);
        assertThat(result.approvedAt()).isNotNull();
        var captor = ArgumentCaptor.forClass(MerchantApprovedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(owner);
        assertThat(captor.getValue().correlationId()).isEqualTo(correlation);
    }

    @Test void rejectionSavesReasonWithoutApprovalEvent() {
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(pending()));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.reject(merchantId, " Missing documents ");
        assertThat(result.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo("Missing documents");
        assertThat(result.approvedAt()).isNull();
        verifyNoInteractions(events);
    }

    @Test void repeatedDecisionIsConflict() {
        var account = pending();
        account.setStatus(MerchantStatus.APPROVED);
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.approve(merchantId, admin, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(() -> service.reject(merchantId, "Invalid"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(events);
        verify(accounts, never()).save(any());
    }

    @Test void ownerlessLegacyAccountCannotBeApproved() {
        var account = pending();
        account.setOwnerUserId(null);
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.approve(merchantId, admin, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(events);
    }

    @Test void listUsesOptionalStatusFilter() {
        when(accounts.findByStatus(MerchantStatus.PENDING_APPROVAL)).thenReturn(List.of(pending()));
        when(accounts.findAll()).thenReturn(List.of(pending()));
        assertThat(service.list(MerchantStatus.PENDING_APPROVAL)).hasSize(1);
        assertThat(service.list(null)).hasSize(1);
    }

    private MerchantAccount pending() {
        return MerchantAccount.builder().merchantId(merchantId).ownerUserId(owner)
                .status(MerchantStatus.PENDING_APPROVAL).build();
    }
}
