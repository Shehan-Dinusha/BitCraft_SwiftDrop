package com.swiftdrop.order.domain.repository;

import com.swiftdrop.order.domain.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
}
