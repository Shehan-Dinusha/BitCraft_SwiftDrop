package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantOrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MerchantOrderReceiptRepository extends JpaRepository<MerchantOrderReceipt, UUID> {
    Optional<MerchantOrderReceipt> findByMerchantIdAndExternalRef(UUID merchantId, String externalRef);
}
