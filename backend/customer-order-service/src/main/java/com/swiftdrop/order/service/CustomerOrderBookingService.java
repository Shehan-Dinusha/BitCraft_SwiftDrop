package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.exception.CallerIdentityRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Customer bookings that survive a retry. The browser sends one {@code clientRequestId} per booking; if
 * the response was lost (dropped connection, refresh) and the customer presses Confirm again, the order
 * already created is returned and no second order or {@code order.created} event is made.
 *
 * <p>Not transactional on purpose, like {@link InternalOrderService}: a unique-index violation aborts the
 * PostgreSQL transaction, so the creation runs in its own and a lost race is resolved by reading again.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderBookingService {

    private final CustomerOrderService customerOrderService;
    private final CustomerOrderRepository customerOrderRepository;

    public CustomerOrderResponse book(CreateOrderRequest request, UUID authenticatedUserId, UUID correlationId) {
        UUID customerId = authenticatedUserId != null ? authenticatedUserId : request.getCustomerId();
        UUID requestId = request.getClientRequestId();
        if (customerId == null) {
            throw new CallerIdentityRequiredException("Customer identification is required");
        }
        if (requestId == null) {
            return customerOrderService.createOrder(request, authenticatedUserId, correlationId);
        }

        Optional<CustomerOrderResponse> existing = findExisting(customerId, requestId);
        if (existing.isPresent()) {
            log.info("Booking retried with a known clientRequestId; returning the existing order");
            return existing.get();
        }
        try {
            return customerOrderService.createOrder(request, authenticatedUserId, correlationId);
        } catch (DataIntegrityViolationException e) {
            // A concurrent retry with the same clientRequestId committed first.
            return findExisting(customerId, requestId).orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrderResponse> findExisting(UUID customerId, UUID clientRequestId) {
        return customerOrderRepository.findByCustomerIdAndClientRequestId(customerId, clientRequestId)
                .map(CustomerOrderResponse::fromEntity);
    }
}
