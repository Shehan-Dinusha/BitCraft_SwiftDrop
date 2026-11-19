package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.entity.*;
import com.swiftdrop.merchant.domain.repository.*;
import com.swiftdrop.merchant.dto.*;
import com.swiftdrop.merchant.exception.MerchantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class B2bOrderSubmissionService {
    private final MerchantAccountRepository merchants;
    private final MerchantOrderReceiptRepository receipts;
    private final MerchantOrderNormalizer normalizer;
    private final OrderServiceClient orders;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public B2bOrderResult submit(UUID merchantId, B2bOrderRequest request, UUID correlationId) {
        // Serialize submissions per merchant across replicas, including the local receipt write.
        MerchantAccount merchant = merchants.findForUpdate(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found"));
        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant approval required");
        }
        var body = normalizer.normalize(merchantId, request);
        var existing = receipts.findByMerchantIdAndExternalRef(merchantId, request.externalRef());
        if (existing.isPresent()) {
            var receipt = existing.get();
            return new B2bOrderResult(request.externalRef(), receipt.getOrderId(), receipt.getTrackingNumber(), false, null);
        }
        var result = orders.create(body, correlationId != null ? correlationId : UUID.randomUUID());
        receipts.save(MerchantOrderReceipt.builder().merchantId(merchantId).externalRef(request.externalRef())
                .orderId(result.orderId()).trackingNumber(result.trackingNumber()).build());
        return result;
    }
}
