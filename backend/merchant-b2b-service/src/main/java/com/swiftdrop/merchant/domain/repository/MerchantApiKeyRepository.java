package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {
    Optional<MerchantApiKey> findByKeyHash(String keyHash);
    Optional<MerchantApiKey> findByKeyId(UUID keyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from MerchantApiKey k where k.keyId = :keyId")
    Optional<MerchantApiKey> findForUpdate(@Param("keyId") UUID keyId);
    List<MerchantApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    List<MerchantApiKey> findByMerchantIdAndActiveTrue(UUID merchantId);
}
