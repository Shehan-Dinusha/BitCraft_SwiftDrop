package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.InternalCreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent creation of merchant orders.
 *
 * <p>This class is intentionally not transactional. A unique-index violation aborts the surrounding
 * PostgreSQL transaction, so the creation runs in its own transaction
 * ({@link CustomerOrderService#createMerchantOrder}) and, if a concurrent request with the same
 * (merchantId, externalRef) won the race, the existing order is read again in a fresh transaction.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalOrderService {

    private final CustomerOrderService customerOrderService;
    private final CustomerOrderRepository customerOrderRepository;

    /** Result of a create request: the order, and whether this call created it. */
    public record CreateResult(CustomerOrderResponse order, boolean created) {
    }

    public CreateResult createMerchantOrder(InternalCreateOrderRequest request, UUID correlationId) {
        Optional<CustomerOrderResponse> existing = findExisting(request);
        if (existing.isPresent()) {
            log.info("Merchant order already exists for merchantId={}, returning it", request.merchantId());
            return new CreateResult(existing.get(), false);
        }

        try {
            return new CreateResult(customerOrderService.createMerchantOrder(request, correlationId), true);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same (merchantId, externalRef) may have committed first.
            Optional<CustomerOrderResponse> winner = findExisting(request);
            if (winner.isPresent()) {
                log.info("Concurrent merchant order creation resolved for merchantId={}, returning the existing order",
                        request.merchantId());
                return new CreateResult(winner.get(), false);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrderResponse> findExisting(InternalCreateOrderRequest request) {
        return customerOrderRepository.findByMerchantIdAndExternalRef(request.merchantId(), request.externalRef())
                .map(CustomerOrderResponse::fromEntity);
    }
}
