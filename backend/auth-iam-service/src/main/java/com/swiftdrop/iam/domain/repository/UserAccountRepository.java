package com.swiftdrop.iam.domain.repository;

import com.swiftdrop.iam.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
}
