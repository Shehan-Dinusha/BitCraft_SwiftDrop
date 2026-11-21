package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.merchant.MerchantApplicationEvent;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.MerchantApprovalResponse;
import com.swiftdrop.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.merchant.messaging.event.MerchantApprovedEvent;
import com.swiftdrop.merchant.security.MerchantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Merchant business applications.
 *
 * <ul>
 *   <li>A merchant user has one application. After a rejection they edit it and submit it again.</li>
 *   <li>The dispatcher of the business's district approves or rejects it; a SYSTEM_ADMIN can decide
 *       for any district, and suspends or reactivates approved merchants.</li>
 *   <li>Each submission and decision is announced on {@code notification.merchant.application}, so the
 *       district dispatcher is alerted and the owner emailed. Approval also publishes
 *       {@code merchant.approved}, which links the owner's IAM account to the merchant company.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MerchantApprovalService {
    private final MerchantAccountRepository accounts;
    private final ApplicationEventPublisher events;

    /** Submits the caller's application, or resubmits it with new details after a rejection. */
    @Transactional
    public MerchantApprovalResponse register(UUID owner, RegisterMerchantRequest request) {
        Optional<MerchantAccount> existing = accounts.findFirstByOwnerUserIdOrderByCreatedAtDesc(owner);
        MerchantAccount account;
        if (existing.isPresent()) {
            account = existing.get();
            switch (account.getStatus()) {
                case PENDING_APPROVAL -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Your application is already under review");
                case APPROVED, SUSPENDED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Your business is already registered");
                case REJECTED -> {
                    // Resubmission: the edited details go back to the district dispatcher.
                }
            }
        } else {
            account = MerchantAccount.builder().ownerUserId(owner).build();
        }
        String regNo = blankToNull(request.businessRegNo());
        String website = website(request.websiteUrl());
        if (regNo == null && website == null) {
            // The dispatcher needs something to verify; many small sellers have a page but no registration.
            throw new IllegalArgumentException(
                    "Give a business registration number or a link to your website or social media page");
        }
        account.setBusinessName(request.businessName().trim());
        account.setBusinessCategory(request.businessCategory());
        account.setMonthlyParcelVolume(request.monthlyParcelVolume());
        account.setBusinessRegNo(regNo == null ? null : regNo.toUpperCase(java.util.Locale.ROOT));
        account.setWebsiteUrl(website);
        account.setContactEmail(request.contactEmail().trim());
        account.setContactPhone(phone(request.contactPhone()));
        account.setPickupAddress(request.pickupAddress().trim());
        account.setPickupCity(request.pickupCity().trim());
        account.setPickupPostalCode(request.pickupPostalCode().trim());
        account.setOperatingDistrict(request.operatingDistrict());
        account.setStatus(MerchantStatus.PENDING_APPROVAL);
        account.setRejectionReason(null);
        account.setApprovedBy(null);
        account.setApprovedAt(null);
        MerchantAccount saved = accounts.save(account);
        announce(saved, MerchantApplicationEvent.Outcome.SUBMITTED, null, null);
        return MerchantApprovalResponse.from(saved);
    }

    /** The caller's own application, whatever its status. */
    @Transactional(readOnly = true)
    public MerchantApprovalResponse myApplication(UUID owner) {
        return accounts.findFirstByOwnerUserIdOrderByCreatedAtDesc(owner)
                .map(MerchantApprovalResponse::from)
                .orElseThrow(() -> new MerchantNotFoundException("You have not applied yet"));
    }

    /** Applications a reviewer may see: a dispatcher's own district, or every district for an admin. */
    @Transactional(readOnly = true)
    public List<MerchantApprovalResponse> list(CallerContext reviewer, MerchantStatus status,
                                               SriLankanDistrict requestedDistrict) {
        SriLankanDistrict district = MerchantAccess.reviewDistrict(reviewer, requestedDistrict);
        List<MerchantAccount> found;
        if (district == null) {
            found = status == null ? accounts.findAll() : accounts.findByStatus(status);
        } else {
            found = status == null ? accounts.findByOperatingDistrict(district)
                    : accounts.findByOperatingDistrictAndStatus(district, status);
        }
        return found.stream().map(MerchantApprovalResponse::from).toList();
    }

    @Transactional
    public MerchantApprovalResponse approve(CallerContext reviewer, UUID merchantId, UUID correlationId) {
        MerchantAccount account = pending(reviewer, merchantId);
        if (account.getOwnerUserId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business must register an owner before approval");
        }
        Instant now = Instant.now();
        account.setStatus(MerchantStatus.APPROVED);
        account.setApprovedBy(reviewer.userId());
        account.setApprovedAt(now);
        account.setRejectionReason(null);
        accounts.save(account);
        events.publishEvent(new MerchantApprovedEvent(null, correlationId, merchantId, account.getOwnerUserId(), now, now));
        announce(account, MerchantApplicationEvent.Outcome.APPROVED, null, correlationId);
        return MerchantApprovalResponse.from(account);
    }

    @Transactional
    public MerchantApprovalResponse reject(CallerContext reviewer, UUID merchantId, String reason) {
        MerchantAccount account = pending(reviewer, merchantId);
        account.setStatus(MerchantStatus.REJECTED);
        account.setRejectionReason(reason.trim());
        account.setApprovedBy(null);
        account.setApprovedAt(null);
        MerchantAccount saved = accounts.save(account);
        announce(saved, MerchantApplicationEvent.Outcome.REJECTED, saved.getRejectionReason(), null);
        return MerchantApprovalResponse.from(saved);
    }

    /**
     * Admin oversight: a suspended merchant's API keys stop working and it cannot upload orders until
     * reactivated. Only an approved merchant can be suspended.
     */
    @Transactional
    public MerchantApprovalResponse suspend(UUID merchantId) {
        MerchantAccount account = locked(merchantId);
        if (account.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an approved merchant can be suspended");
        }
        account.setStatus(MerchantStatus.SUSPENDED);
        return MerchantApprovalResponse.from(accounts.save(account));
    }

    @Transactional
    public MerchantApprovalResponse reactivate(UUID merchantId) {
        MerchantAccount account = locked(merchantId);
        if (account.getStatus() != MerchantStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a suspended merchant can be reactivated");
        }
        account.setStatus(MerchantStatus.APPROVED);
        return MerchantApprovalResponse.from(accounts.save(account));
    }

    private MerchantAccount pending(CallerContext reviewer, UUID merchantId) {
        MerchantAccount account = locked(merchantId);
        MerchantAccess.requireReviewer(reviewer, account.getOperatingDistrict());
        if (account.getStatus() != MerchantStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending merchants can be approved or rejected");
        }
        return account;
    }

    private MerchantAccount locked(UUID merchantId) {
        return accounts.findForUpdate(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "facebook.com/shop" -> "https://facebook.com/shop". The request pattern has already checked the shape. */
    static String website(String value) {
        String url = blankToNull(value);
        if (url == null) return null;
        return url.matches("(?i)^https?://.*") ? url : "https://" + url;
    }

    /** "0771234567" -> "+94771234567", the format the rest of the platform stores. */
    static String phone(String value) {
        String number = value.trim();
        return number.startsWith("0") ? "+94" + number.substring(1) : number;
    }

    private void announce(MerchantAccount account, MerchantApplicationEvent.Outcome outcome, String reason,
                          UUID correlationId) {
        events.publishEvent(new MerchantApplicationEvent(null, correlationId, account.getMerchantId(),
                account.getOwnerUserId(), account.getBusinessName(), account.getOperatingDistrict(), outcome,
                reason, null));
    }
}
