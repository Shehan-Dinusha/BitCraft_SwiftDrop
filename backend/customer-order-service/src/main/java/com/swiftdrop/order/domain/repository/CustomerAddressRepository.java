package com.swiftdrop.order.domain.repository;

import com.swiftdrop.order.domain.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    Optional<CustomerAddress> findByAddressIdAndCustomerId(UUID addressId, UUID customerId);

    long countByCustomerId(UUID customerId);
}
