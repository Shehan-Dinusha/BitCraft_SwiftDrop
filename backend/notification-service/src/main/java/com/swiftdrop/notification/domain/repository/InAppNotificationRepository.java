package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A user's inbox is their personal rows plus the rows of their team audiences. Callers always pass a
 * non-empty {@code audiences} collection (use a placeholder when the user belongs to no team).
 */
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT n FROM InAppNotification n WHERE n.userId = :userId OR n.audience IN :audiences "
            + "ORDER BY n.createdAt DESC")
    List<InAppNotification> findInbox(@Param("userId") UUID userId, @Param("audiences") Collection<String> audiences);

    @Query("SELECT n FROM InAppNotification n WHERE n.id = :id AND (n.userId = :userId OR n.audience IN :audiences)")
    Optional<InAppNotification> findInInbox(@Param("id") Long id, @Param("userId") UUID userId,
                                            @Param("audiences") Collection<String> audiences);

    @Query("SELECT COUNT(n) FROM InAppNotification n WHERE n.read = false "
            + "AND (n.userId = :userId OR n.audience IN :audiences)")
    long countUnreadInInbox(@Param("userId") UUID userId, @Param("audiences") Collection<String> audiences);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.read = true WHERE n.read = false "
            + "AND (n.userId = :userId OR n.audience IN :audiences)")
    int markInboxRead(@Param("userId") UUID userId, @Param("audiences") Collection<String> audiences);
}
