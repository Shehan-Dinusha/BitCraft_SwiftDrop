package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.OrderMerchantMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Read access to the order ownership projection. Rows are created through {@link IngestionJdbcRepository}. */
public interface OrderMerchantMappingRepository extends JpaRepository<OrderMerchantMapping, UUID> {

    List<OrderMerchantMapping> findByMerchantId(UUID merchantId);
}
