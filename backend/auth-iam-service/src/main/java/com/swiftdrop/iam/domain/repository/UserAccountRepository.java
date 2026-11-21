package com.swiftdrop.iam.domain.repository;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>,
        JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByPublicId(UUID publicId);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    /** The account whose pending staff invite token hashes to {@code inviteTokenHash}. */
    Optional<UserAccount> findByInviteTokenHash(String inviteTokenHash);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    long countByRole(UserRole role);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    /** Removes abandoned self-service sign-ups that never confirmed their email address. */
    @Modifying
    @Query("delete from UserAccount u where u.emailVerified = false and u.createdAt < :cutoff")
    int deleteUnverifiedCreatedBefore(@Param("cutoff") Instant cutoff);
}
