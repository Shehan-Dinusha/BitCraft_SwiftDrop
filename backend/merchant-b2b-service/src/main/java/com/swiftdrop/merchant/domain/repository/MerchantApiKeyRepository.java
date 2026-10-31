package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {
    Optional<MerchantApiKey> findByKeyHash(String keyHash);
    Optional<MerchantApiKey> findByKeyId(UUID keyId);
    List<MerchantApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    List<MerchantApiKey> findByMerchantIdAndActiveTrue(UUID merchantId);
}
