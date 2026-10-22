package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {
}
