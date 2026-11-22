package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.delivery.domain.entity.MerchantPayment;
import com.swiftdrop.delivery.domain.repository.CashEntryRepository;
import com.swiftdrop.delivery.domain.repository.MerchantPaymentRepository;
import com.swiftdrop.delivery.dto.CashEntryResponse;
import com.swiftdrop.delivery.dto.MerchantPaymentResponse;
import com.swiftdrop.delivery.exception.CashRecordNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** A shop's receipts: what it paid, when, to whom and for which parcels. A shop sees only its own. */
@Service
public class MerchantPaymentService {

    private static final int PAGE_SIZE = 20;

    private final MerchantPaymentRepository payments;
    private final CashEntryRepository entries;

    public MerchantPaymentService(MerchantPaymentRepository payments, CashEntryRepository entries) {
        this.payments = payments;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public List<MerchantPaymentResponse> list(CallerContext caller, int page) {
        return payments.findByMerchantIdOrderByPaidAtDesc(merchant(caller), PageRequest.of(Math.max(0, page), PAGE_SIZE))
                .map(MerchantPaymentResponse::of).getContent();
    }

    @Transactional(readOnly = true)
    public List<CashEntryResponse> entries(CallerContext caller, UUID paymentId) {
        UUID merchantId = merchant(caller);
        MerchantPayment payment = payments.findByPaymentId(paymentId)
                .filter(found -> found.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new CashRecordNotFoundException("Payment not found."));
        return entries.findByPaymentIdOrderByTrackingNumber(payment.getPaymentId()).stream()
                .map(CashEntryResponse::of).toList();
    }

    private static UUID merchant(CallerContext caller) {
        caller.requireRole(UserRole.MERCHANT);
        if (caller.merchantId() == null) {
            throw new ForbiddenException("Your account is not linked to a business");
        }
        return caller.merchantId();
    }
}
