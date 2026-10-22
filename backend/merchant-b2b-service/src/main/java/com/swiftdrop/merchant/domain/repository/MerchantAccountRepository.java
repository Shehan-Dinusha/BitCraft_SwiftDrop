package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, Long> {
}
