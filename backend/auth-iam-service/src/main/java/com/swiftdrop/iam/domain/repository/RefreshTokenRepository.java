package com.swiftdrop.iam.domain.repository;

import com.swiftdrop.iam.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
