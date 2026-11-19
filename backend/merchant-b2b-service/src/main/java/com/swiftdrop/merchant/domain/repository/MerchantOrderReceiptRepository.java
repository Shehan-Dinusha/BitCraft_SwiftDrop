package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantOrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MerchantOrderReceiptRepository extends JpaRepository<MerchantOrderReceipt, UUID> {
    org.springframework.data.domain.Page<MerchantOrderReceipt> findByMerchantIdOrderByCreatedAtDesc(
            UUID merchantId, org.springframework.data.domain.Pageable pageable);
    Optional<MerchantOrderReceipt> findByMerchantIdAndExternalRef(UUID merchantId, String externalRef);
}
