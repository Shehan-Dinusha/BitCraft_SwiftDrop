package com.swiftdrop.order.domain.repository;

import com.swiftdrop.order.domain.entity.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderId(UUID orderId);

    /**
     * Loads the order with a row lock held until the transaction ends, so status writers
     * (event sync, cancel, fee revision) run one at a time per order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.orderId = :orderId")
    Optional<CustomerOrder> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    Optional<CustomerOrder> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    Optional<CustomerOrder> findByMerchantIdAndExternalRef(UUID merchantId, String externalRef);

    Page<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
