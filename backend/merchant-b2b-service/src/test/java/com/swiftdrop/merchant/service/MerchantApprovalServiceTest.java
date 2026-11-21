package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.merchant.MerchantApplicationEvent;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
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
    CallerContext adminCaller = new CallerContext(admin, UserRole.SYSTEM_ADMIN, null, null);
    CallerContext colomboDispatcher = new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.COLOMBO);
    CallerContext galleDispatcher = new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, SriLankanDistrict.GALLE);

    @BeforeEach void setup() { service = new MerchantApprovalService(accounts, events); }

    @Test void registrationSavesOwnerAndPendingStatusAndAlertsTheDistrict() {
        when(accounts.findFirstByOwnerUserIdOrderByCreatedAtDesc(owner)).thenReturn(Optional.empty());
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.register(owner, request(" Shop "));
        assertThat(result.ownerUserId()).isEqualTo(owner);
        assertThat(result.businessRegNo()).isEqualTo("BR-1");
        assertThat(result.businessName()).isEqualTo("Shop");
        assertThat(result.status()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        MerchantApplicationEvent event = captureApplicationEvent();
        assertThat(event.outcome()).isEqualTo(MerchantApplicationEvent.Outcome.SUBMITTED);
        assertThat(event.operatingDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
    }

    @Test void aRejectedApplicationCanBeCorrectedAndResubmitted() {
        var rejected = pending();
        rejected.setStatus(MerchantStatus.REJECTED);
        rejected.setRejectionReason("Registration number does not match");
        when(accounts.findFirstByOwnerUserIdOrderByCreatedAtDesc(owner)).thenReturn(Optional.of(rejected));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.register(owner, request("Shop Pvt Ltd"));

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.status()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        assertThat(result.rejectionReason()).isNull();
        assertThat(result.businessName()).isEqualTo("Shop Pvt Ltd");
    }

    @Test void anApplicationUnderReviewOrApprovedCannotBeSubmittedAgain() {
        for (MerchantStatus status : List.of(MerchantStatus.PENDING_APPROVAL, MerchantStatus.APPROVED, MerchantStatus.SUSPENDED)) {
            var account = pending();
            account.setStatus(status);
            when(accounts.findFirstByOwnerUserIdOrderByCreatedAtDesc(owner)).thenReturn(Optional.of(account));
            assertThatThrownBy(() -> service.register(owner, request("Shop")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        }
        verify(accounts, never()).save(any());
    }

    @Test void theDistrictDispatcherApprovesAndBothEventsArePublished() {
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(pending()));
        UUID correlation = UUID.randomUUID();
        var result = service.approve(colomboDispatcher, merchantId, correlation);
        assertThat(result.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(result.approvedBy()).isEqualTo(colomboDispatcher.userId());
        assertThat(result.approvedAt()).isNotNull();
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(captor.capture());
        var approved = (MerchantApprovedEvent) captor.getAllValues().get(0);
        assertThat(approved.merchantId()).isEqualTo(merchantId);
        assertThat(approved.ownerUserId()).isEqualTo(owner);
        assertThat(approved.correlationId()).isEqualTo(correlation);
        var application = (MerchantApplicationEvent) captor.getAllValues().get(1);
        assertThat(application.outcome()).isEqualTo(MerchantApplicationEvent.Outcome.APPROVED);
        assertThat(application.recipientId()).isEqualTo(owner);
    }

    @Test void aDispatcherCannotDecideOnAnotherDistrictsApplication() {
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(pending()));
        assertThatThrownBy(() -> service.approve(galleDispatcher, merchantId, null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.reject(galleDispatcher, merchantId, "No")).isInstanceOf(ForbiddenException.class);
        verify(accounts, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test void anAdminCanDecideForAnyDistrict() {
        var galle = pending();
        galle.setOperatingDistrict(SriLankanDistrict.GALLE);
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(galle));
        assertThat(service.approve(adminCaller, merchantId, null).approvedBy()).isEqualTo(admin);
    }

    @Test void rejectionSavesReasonAndEmailsTheOwner() {
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(pending()));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.reject(colomboDispatcher, merchantId, " Missing documents ");
        assertThat(result.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo("Missing documents");
        assertThat(result.approvedAt()).isNull();
        MerchantApplicationEvent event = captureApplicationEvent();
        assertThat(event.outcome()).isEqualTo(MerchantApplicationEvent.Outcome.REJECTED);
        assertThat(event.reason()).isEqualTo("Missing documents");
    }

    @Test void repeatedDecisionIsConflict() {
        var account = pending();
        account.setStatus(MerchantStatus.APPROVED);
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.approve(adminCaller, merchantId, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(() -> service.reject(adminCaller, merchantId, "Invalid"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(events);
        verify(accounts, never()).save(any());
    }

    @Test void ownerlessLegacyAccountCannotBeApproved() {
        var account = pending();
        account.setOwnerUserId(null);
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.approve(adminCaller, merchantId, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(events);
    }

    @Test void aDispatcherListsOnlyTheirDistrictWhateverTheyAskFor() {
        when(accounts.findByOperatingDistrictAndStatus(SriLankanDistrict.COLOMBO, MerchantStatus.PENDING_APPROVAL))
                .thenReturn(List.of(pending()));
        assertThat(service.list(colomboDispatcher, MerchantStatus.PENDING_APPROVAL, SriLankanDistrict.GALLE)).hasSize(1);
        verify(accounts, never()).findAll();
    }

    @Test void anAdminListsEveryDistrictOrTheOneChosen() {
        when(accounts.findAll()).thenReturn(List.of(pending()));
        when(accounts.findByOperatingDistrict(SriLankanDistrict.KANDY)).thenReturn(List.of());
        assertThat(service.list(adminCaller, null, null)).hasSize(1);
        assertThat(service.list(adminCaller, null, SriLankanDistrict.KANDY)).isEmpty();
    }

    @Test void onlyAnApprovedMerchantCanBeSuspendedAndOnlyASuspendedOneReactivated() {
        var account = pending();
        account.setStatus(MerchantStatus.APPROVED);
        when(accounts.findForUpdate(merchantId)).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.suspend(merchantId).status()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThatThrownBy(() -> service.suspend(merchantId)).hasMessageContaining("409");
        assertThat(service.reactivate(merchantId).status()).isEqualTo(MerchantStatus.APPROVED);
        assertThatThrownBy(() -> service.reactivate(merchantId)).hasMessageContaining("409");
    }

    @Test void myApplicationIsNotFoundBeforeApplying() {
        when(accounts.findFirstByOwnerUserIdOrderByCreatedAtDesc(owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.myApplication(owner))
                .isInstanceOf(com.swiftdrop.merchant.exception.MerchantNotFoundException.class);
    }

    private MerchantApplicationEvent captureApplicationEvent() {
        var captor = ArgumentCaptor.forClass(MerchantApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static RegisterMerchantRequest request(String name) {
        return new RegisterMerchantRequest(name, "BR-1", "shop@example.com", "+94771234567", "Colombo",
                SriLankanDistrict.COLOMBO);
    }

    private MerchantAccount pending() {
        return MerchantAccount.builder().merchantId(merchantId).ownerUserId(owner).businessName("Shop")
                .operatingDistrict(SriLankanDistrict.COLOMBO).status(MerchantStatus.PENDING_APPROVAL).build();
    }
}
