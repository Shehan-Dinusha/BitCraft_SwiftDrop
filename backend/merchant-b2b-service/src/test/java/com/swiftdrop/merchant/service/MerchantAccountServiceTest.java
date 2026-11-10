package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.CreateMerchantRequest;
import com.swiftdrop.merchant.dto.MerchantAccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAccountServiceTest {

    @Mock
    private MerchantAccountRepository merchantAccountRepository;

    private MerchantAccountService merchantAccountService;

    @BeforeEach
    void setUp() {
        merchantAccountService = new MerchantAccountService(merchantAccountRepository);
    }

    @Test
    void createMerchantStartsPendingApproval() {
        CreateMerchantRequest request = new CreateMerchantRequest(
                "Lanka Retail Hub",
                "orders@retail.example",
                "+94 11 222 3333",
                "10 Galle Road, Colombo",
                SriLankanDistrict.COLOMBO
        );
        when(merchantAccountRepository.save(any(MerchantAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantAccountResponse response = merchantAccountService.createMerchant(request);

        ArgumentCaptor<MerchantAccount> accountCaptor = ArgumentCaptor.forClass(MerchantAccount.class);
        verify(merchantAccountRepository).save(accountCaptor.capture());
        MerchantAccount saved = accountCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        assertThat(saved.getOwnerUserId()).isNull();
        assertThat(saved.getBusinessRegNo()).isNull();
        assertThat(saved.getApprovedBy()).isNull();
        assertThat(saved.getApprovedAt()).isNull();
        assertThat(saved.getRejectionReason()).isNull();
        assertThat(response.status()).isEqualTo(MerchantStatus.PENDING_APPROVAL.name());
    }

    @Test
    void getOrCreateMerchantStartsProvisionedAccountPendingApproval() {
        UUID merchantId = UUID.randomUUID();
        when(merchantAccountRepository.findByMerchantId(merchantId)).thenReturn(Optional.empty());
        when(merchantAccountRepository.save(any(MerchantAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantAccountResponse response =
                merchantAccountService.getOrCreateMerchant(merchantId, "Provisioned Merchant");

        ArgumentCaptor<MerchantAccount> accountCaptor = ArgumentCaptor.forClass(MerchantAccount.class);
        verify(merchantAccountRepository).save(accountCaptor.capture());

        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(MerchantStatus.PENDING_APPROVAL);
        assertThat(response.status()).isEqualTo(MerchantStatus.PENDING_APPROVAL.name());
    }
}
