package com.swiftdrop.order.domain.repository;

import com.swiftdrop.order.domain.entity.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderId(UUID orderId);

    Optional<CustomerOrder> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    Page<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
