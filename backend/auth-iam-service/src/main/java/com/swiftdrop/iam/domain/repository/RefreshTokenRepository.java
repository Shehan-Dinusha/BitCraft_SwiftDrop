package com.swiftdrop.iam.domain.repository;

import com.swiftdrop.iam.domain.entity.RefreshToken;
import com.swiftdrop.iam.domain.entity.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revoked = true where token.user = :user and token.revoked = false")
    int revokeAllForUser(@Param("user") UserAccount user);

    @Modifying
    @Query("delete from RefreshToken token where token.expiresAt < :cutoff or token.revoked = true")
    int deleteExpiredOrRevoked(@Param("cutoff") Instant cutoff);
}
