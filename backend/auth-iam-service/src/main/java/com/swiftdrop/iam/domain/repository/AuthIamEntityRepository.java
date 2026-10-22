package com.swiftdrop.iam.domain.repository;

import com.swiftdrop.iam.domain.entity.AuthIamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIamEntityRepository extends JpaRepository<AuthIamEntity, Long> {
}
