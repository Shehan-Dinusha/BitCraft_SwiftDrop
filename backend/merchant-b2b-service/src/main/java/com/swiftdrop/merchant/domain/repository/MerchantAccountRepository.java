package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, Long> {
    Optional<MerchantAccount> findByMerchantId(UUID merchantId);
    Optional<MerchantAccount> findByContactEmail(String contactEmail);
}
