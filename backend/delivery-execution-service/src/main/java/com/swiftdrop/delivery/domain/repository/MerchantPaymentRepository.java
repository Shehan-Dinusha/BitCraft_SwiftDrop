package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.delivery.domain.entity.MerchantPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantPaymentRepository extends JpaRepository<MerchantPayment, Long> {

    Optional<MerchantPayment> findByPaymentId(UUID paymentId);

    Page<MerchantPayment> findByMerchantIdOrderByPaidAtDesc(UUID merchantId, Pageable page);
}
