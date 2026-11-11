package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.exception.MerchantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantApprovalGuard {
    private final MerchantAccountRepository accounts;

    public MerchantAccount requireApproved(UUID merchantId) {
        MerchantAccount account = accounts.findByMerchantId(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
        if (account.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant approval required");
        }
        return account;
    }
}
