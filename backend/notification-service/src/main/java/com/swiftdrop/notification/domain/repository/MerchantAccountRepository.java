package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, UUID> {
}
