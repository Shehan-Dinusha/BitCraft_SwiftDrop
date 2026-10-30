package com.swiftdrop.order.service;

import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CancelOrderRequest;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.dto.UpdateOrderRequest;
import com.swiftdrop.order.exception.InvalidOrderStateException;
import com.swiftdrop.order.exception.OrderNotFoundException;
import com.swiftdrop.order.messaging.publisher.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderService {

    private final CustomerOrderRepository customerOrderRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final PricingCalculationService pricingCalculationService;
    private final OrderPdfGeneratorService orderPdfGeneratorService;
    private final OrderEventPublisher orderEventPublisher;

    public FeeEstimateResponse calculateEstimate(FeeEstimateRequest request) {
        return pricingCalculationService.calculateEstimate(request);
    }

    @Transactional
    public CustomerOrderResponse createOrder(CreateOrderRequest request, UUID authenticatedUserId, UUID correlationId) {
        UUID effectiveCustomerId = authenticatedUserId != null ? authenticatedUserId : request.getCustomerId();
        if (effectiveCustomerId == null) {
            throw new IllegalArgumentException("Customer ID is mandatory");
        }

        // 1. Calculate price and chargeable weight
        FeeEstimateRequest estimateReq = FeeEstimateRequest.builder()
                .pickupDistrict(request.getPickupDistrict())
                .deliveryDistrict(request.getDeliveryDistrict())
                .actualWeightKg(request.getActualWeightKg())
                .lengthCm(request.getLengthCm())
                .widthCm(request.getWidthCm())
                .heightCm(request.getHeightCm())
                .speed(request.getSpeed() != null ? request.getSpeed() : OrderSpeed.STANDARD)
                .build();
        FeeEstimateResponse estimate = pricingCalculationService.calculateEstimate(estimateReq);

        // 2. Generate non-sequential tracking number (SD-XXXX-XXXX)
        String trackingNumber = trackingNumberGenerator.generateUniqueTrackingNumber();

        // 3. Persist CustomerOrder entity
        CustomerOrder order = CustomerOrder.builder()
                .orderId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .customerId(effectiveCustomerId)
                .merchantId(request.getMerchantId())
                .orderSpeed(estimate.getSpeed())
                .status(PublicOrderStatus.CREATED)
                .internalQueueStatus(InternalQueueStatus.CREATED)
                .pickupAddressLine1(request.getPickupAddressLine1())
                .pickupAddressLine2(request.getPickupAddressLine2())
                .pickupCity(request.getPickupCity())
                .pickupDistrict(request.getPickupDistrict())
                .pickupPostalCode(request.getPickupPostalCode())
                .pickupContactName(request.getPickupContactName())
                .pickupContactPhone(request.getPickupContactPhone())
                .deliveryAddressLine1(request.getDeliveryAddressLine1())
                .deliveryAddressLine2(request.getDeliveryAddressLine2())
                .deliveryCity(request.getDeliveryCity())
                .deliveryDistrict(request.getDeliveryDistrict())
                .deliveryPostalCode(request.getDeliveryPostalCode())
                .recipientName(request.getRecipientName())
                .recipientPhone(request.getRecipientPhone())
                .recipientEmail(request.getRecipientEmail())
                .lengthCm(request.getLengthCm())
                .widthCm(request.getWidthCm())
                .heightCm(request.getHeightCm())
                .actualWeightKg(request.getActualWeightKg())
                .chargeableWeightKg(estimate.getChargeableWeightKg())
                .itemDescription(request.getItemDescription())
                .declaredValue(request.getDeclaredValue() != null ? request.getDeclaredValue() : BigDecimal.ZERO)
                .codAmount(request.getCodAmount() != null ? request.getCodAmount() : BigDecimal.ZERO)
                .prohibitedItemsDeclared(request.getProhibitedItemsDeclared())
                .pricingRuleVersionId(estimate.getPricingRuleVersionId())
                .estimatedFee(estimate.getEstimatedFee())
                .finalFee(estimate.getEstimatedFee())
                .currency("LKR")
                .feeRevisionStatus(FeeRevisionStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        CustomerOrder saved = customerOrderRepository.save(order);
        log.info("Created customer order orderId={}, trackingNumber={}", saved.getOrderId(), saved.getTrackingNumber());

        // 4. Publish order.created and parcel.status.changed events
        orderEventPublisher.publishOrderCreated(saved, correlationId);

        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse getOrderById(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return CustomerOrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse getOrderByTrackingNumber(String trackingNumber) {
        CustomerOrder order = customerOrderRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        return CustomerOrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public PagedResult<CustomerOrderResponse> listCustomerOrders(UUID requestedCustomerId, UUID callerId, String callerRole, Pageable pageable) {
        // Only dispatchers and admins may list another customer's orders; everyone else sees their own.
        UUID customerId = isPrivilegedRole(callerRole) && requestedCustomerId != null ? requestedCustomerId : callerId;
        if (customerId == null) {
            throw new IllegalArgumentException("Customer identification is required to list orders");
        }
        Page<CustomerOrder> page = customerOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        return PagedResult.of(
                page.getContent().stream().map(CustomerOrderResponse::fromEntity).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * Ownership rule shared by every order endpoint: dispatchers and system admins may access any order,
     * everyone else only their own. A non-owner gets the same 404 as a missing order so existence is not leaked.
     */
    private CustomerOrder findAccessibleOrder(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = customerOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        assertCallerCanAccess(order, callerId, callerRole);
        return order;
    }

    private void assertCallerCanAccess(CustomerOrder order, UUID callerId, String callerRole) {
        if (isPrivilegedRole(callerRole)) {
            return;
        }
        if (callerId == null || !callerId.equals(order.getCustomerId())) {
            throw new OrderNotFoundException("Order not found");
        }
    }

    private boolean isPrivilegedRole(String callerRole) {
        UserRole role = UserRole.fromClaim(callerRole);
        return role == UserRole.DISPATCHER || role == UserRole.SYSTEM_ADMIN;
    }

    @Transactional
    public CustomerOrderResponse updateOrder(UUID orderId, UpdateOrderRequest request, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);

        if (order.getStatus() != PublicOrderStatus.CREATED) {
            throw new InvalidOrderStateException("Order cannot be updated once it leaves CREATED status. Current status: " + order.getStatus());
        }

        if (request.getPickupAddressLine1() != null) order.setPickupAddressLine1(request.getPickupAddressLine1());
        if (request.getPickupAddressLine2() != null) order.setPickupAddressLine2(request.getPickupAddressLine2());
        if (request.getPickupCity() != null) order.setPickupCity(request.getPickupCity());
        if (request.getPickupDistrict() != null) order.setPickupDistrict(request.getPickupDistrict());
        if (request.getPickupPostalCode() != null) order.setPickupPostalCode(request.getPickupPostalCode());
        if (request.getPickupContactName() != null) order.setPickupContactName(request.getPickupContactName());
        if (request.getPickupContactPhone() != null) order.setPickupContactPhone(request.getPickupContactPhone());

        if (request.getDeliveryAddressLine1() != null) order.setDeliveryAddressLine1(request.getDeliveryAddressLine1());
        if (request.getDeliveryAddressLine2() != null) order.setDeliveryAddressLine2(request.getDeliveryAddressLine2());
        if (request.getDeliveryCity() != null) order.setDeliveryCity(request.getDeliveryCity());
        if (request.getDeliveryDistrict() != null) order.setDeliveryDistrict(request.getDeliveryDistrict());
        if (request.getDeliveryPostalCode() != null) order.setDeliveryPostalCode(request.getDeliveryPostalCode());
        if (request.getRecipientName() != null) order.setRecipientName(request.getRecipientName());
        if (request.getRecipientPhone() != null) order.setRecipientPhone(request.getRecipientPhone());
        if (request.getRecipientEmail() != null) order.setRecipientEmail(request.getRecipientEmail());

        if (request.getItemDescription() != null) order.setItemDescription(request.getItemDescription());
        if (request.getDeclaredValue() != null) order.setDeclaredValue(request.getDeclaredValue());
        if (request.getCodAmount() != null) order.setCodAmount(request.getCodAmount());

        boolean dimsOrSpeedChanged = false;
        if (request.getLengthCm() != null) { order.setLengthCm(request.getLengthCm()); dimsOrSpeedChanged = true; }
        if (request.getWidthCm() != null) { order.setWidthCm(request.getWidthCm()); dimsOrSpeedChanged = true; }
        if (request.getHeightCm() != null) { order.setHeightCm(request.getHeightCm()); dimsOrSpeedChanged = true; }
        if (request.getActualWeightKg() != null) { order.setActualWeightKg(request.getActualWeightKg()); dimsOrSpeedChanged = true; }
        if (request.getSpeed() != null) { order.setOrderSpeed(request.getSpeed()); dimsOrSpeedChanged = true; }

        if (dimsOrSpeedChanged) {
            FeeEstimateRequest estimateReq = FeeEstimateRequest.builder()
                    .pickupDistrict(order.getPickupDistrict())
                    .deliveryDistrict(order.getDeliveryDistrict())
                    .actualWeightKg(order.getActualWeightKg())
                    .lengthCm(order.getLengthCm())
                    .widthCm(order.getWidthCm())
                    .heightCm(order.getHeightCm())
                    .speed(order.getOrderSpeed())
                    .build();
            FeeEstimateResponse estimate = pricingCalculationService.calculateEstimate(estimateReq);
            order.setChargeableWeightKg(estimate.getChargeableWeightKg());
            order.setEstimatedFee(estimate.getEstimatedFee());
            order.setFinalFee(estimate.getEstimatedFee());
        }

        order.setUpdatedAt(Instant.now());
        CustomerOrder saved = customerOrderRepository.save(order);
        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteOrder(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);

        if (order.getStatus() != PublicOrderStatus.CREATED) {
            throw new InvalidOrderStateException("Only orders in CREATED status can be deleted. Current status: " + order.getStatus());
        }

        customerOrderRepository.delete(order);
        log.info("Deleted order {}", orderId);
    }

    @Transactional
    public CustomerOrderResponse cancelOrder(UUID orderId, CancelOrderRequest request, UUID callerId, String callerRole, UUID correlationId) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);

        if (order.getStatus().isTerminal()) {
            throw new InvalidOrderStateException("Order " + orderId + " is already in terminal state " + order.getStatus());
        }

        order.setStatus(PublicOrderStatus.CANCELLED);
        order.setInternalQueueStatus(InternalQueueStatus.CANCELLED);
        order.setCancellationReason(request != null ? request.getReason() : "Customer cancelled order");
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        CustomerOrder saved = customerOrderRepository.save(order);
        orderEventPublisher.publishOrderCancelled(saved, order.getCancellationReason(), "CUSTOMER", correlationId);
        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional
    public CustomerOrderResponse acceptFeeRevision(UUID orderId, UUID callerId, String callerRole, UUID correlationId) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);

        if (order.getFeeRevisionStatus() != FeeRevisionStatus.PENDING || order.getRevisedFee() == null) {
            throw new InvalidOrderStateException("Order " + orderId + " does not have a pending fee revision.");
        }

        order.setFinalFee(order.getRevisedFee());
        order.setFeeRevisionStatus(FeeRevisionStatus.APPROVED);
        order.setUpdatedAt(Instant.now());

        CustomerOrder saved = customerOrderRepository.save(order);
        log.info("Customer accepted fee revision for order {}: finalFee is now LKR {}", orderId, saved.getFinalFee());
        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional
    public CustomerOrderResponse rejectFeeRevision(UUID orderId, UUID callerId, String callerRole, UUID correlationId) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);

        if (order.getFeeRevisionStatus() != FeeRevisionStatus.PENDING) {
            throw new InvalidOrderStateException("Order " + orderId + " does not have a pending fee revision.");
        }

        // FR-C8: Auto-cancelling at $0 if rejected
        order.setFinalFee(BigDecimal.ZERO);
        order.setFeeRevisionStatus(FeeRevisionStatus.REJECTED);
        order.setStatus(PublicOrderStatus.CANCELLED);
        order.setInternalQueueStatus(InternalQueueStatus.CANCELLED);
        order.setCancellationReason("Customer rejected courier fee revision of LKR " + order.getRevisedFee());
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        CustomerOrder saved = customerOrderRepository.save(order);
        orderEventPublisher.publishOrderCancelled(saved, order.getCancellationReason(), "CUSTOMER", correlationId);
        log.info("Customer rejected fee revision for order {}: cancelled at LKR 0.00", orderId);
        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public byte[] getShippingLabelPdf(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return orderPdfGeneratorService.generateShippingLabelAndReceiptPdf(order);
    }

    @Transactional(readOnly = true)
    public byte[] getDeliveryReceiptPdf(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return orderPdfGeneratorService.generateDeliveryReceiptPdf(order);
    }
}
