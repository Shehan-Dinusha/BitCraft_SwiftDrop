package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.MerchantApprovalResponse;
import com.swiftdrop.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.merchant.messaging.event.MerchantApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantApprovalService {
    private final MerchantAccountRepository accounts;
    private final ApplicationEventPublisher events;

    @Transactional
    public MerchantApprovalResponse register(UUID owner, RegisterMerchantRequest request) {
        MerchantAccount account = MerchantAccount.builder()
                .ownerUserId(owner).businessName(request.businessName().trim())
                .businessRegNo(request.businessRegNo().trim()).contactEmail(request.contactEmail().trim())
                .contactPhone(request.contactPhone().trim()).pickupAddress(request.pickupAddress().trim())
                .operatingDistrict(request.operatingDistrict()).status(MerchantStatus.PENDING_APPROVAL).build();
        return MerchantApprovalResponse.from(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public List<MerchantApprovalResponse> list(MerchantStatus status) {
        return (status == null ? accounts.findAll() : accounts.findByStatus(status))
                .stream().map(MerchantApprovalResponse::from).toList();
    }

    @Transactional
    public MerchantApprovalResponse approve(UUID merchantId, UUID administrator, UUID correlationId) {
        MerchantAccount account = pending(merchantId);
        if (account.getOwnerUserId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business must register an owner before approval");
        }
        Instant now = Instant.now();
        account.setStatus(MerchantStatus.APPROVED);
        account.setApprovedBy(administrator);
        account.setApprovedAt(now);
        account.setRejectionReason(null);
        accounts.save(account);
        events.publishEvent(new MerchantApprovedEvent(null, correlationId, merchantId, account.getOwnerUserId(), now, now));
        return MerchantApprovalResponse.from(account);
    }

    @Transactional
    public MerchantApprovalResponse reject(UUID merchantId, String reason) {
        MerchantAccount account = pending(merchantId);
        account.setStatus(MerchantStatus.REJECTED);
        account.setRejectionReason(reason.trim());
        account.setApprovedBy(null);
        account.setApprovedAt(null);
        return MerchantApprovalResponse.from(accounts.save(account));
    }

    private MerchantAccount pending(UUID merchantId) {
        MerchantAccount account = accounts.findForUpdate(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
        if (account.getStatus() != MerchantStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending merchants can be approved or rejected");
        }
        return account;
    }
}
