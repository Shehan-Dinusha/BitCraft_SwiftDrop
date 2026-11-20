package com.swiftdrop.iam.domain.repository;

import com.swiftdrop.iam.domain.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
