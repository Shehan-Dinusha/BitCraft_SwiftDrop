package com.swiftdrop.order.service;

import com.swiftdrop.contracts.phone.SriLankanPhone;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.parcel.ParcelLimits;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderSource;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CancelOrderRequest;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.OrderStatusHistoryEntry;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.dto.InternalCreateOrderRequest;
import com.swiftdrop.order.dto.UpdateOrderRequest;
import com.swiftdrop.order.exception.CallerIdentityRequiredException;
import com.swiftdrop.order.exception.InvalidOrderStateException;
import com.swiftdrop.order.exception.OrderNotFoundException;
import com.swiftdrop.order.exception.ParcelTooLargeException;
import com.swiftdrop.order.messaging.publisher.OrderEventPublisher;
import com.swiftdrop.order.service.pricing.PricingClient;
import lombok.RequiredArgsConstructor;
import com.swiftdrop.order.service.promise.DeliveryPromise;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderService {

    private final CustomerOrderRepository customerOrderRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final PricingClient pricingClient;
    private final OrderPdfGeneratorService orderPdfGeneratorService;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final DeliveryPromise deliveryPromise;

    /** The fee and the promised day; Same day between districts or too late in the day is refused. */
    public FeeEstimateResponse calculateEstimate(FeeEstimateRequest request) {
        OrderSpeed speed = request.getSpeed() != null ? request.getSpeed() : OrderSpeed.STANDARD;
        var deliverBy = deliveryPromise.deliverBy(speed, request.getPickupDistrict(), request.getDeliveryDistrict());
        FeeEstimateResponse estimate = pricingClient.estimate(request);
        estimate.setDeliverBy(deliverBy);
        return estimate;
    }

    @Transactional
    public CustomerOrderResponse createOrder(CreateOrderRequest request, UUID authenticatedUserId, UUID correlationId) {
        UUID effectiveCustomerId = authenticatedUserId != null ? authenticatedUserId : request.getCustomerId();
        if (effectiveCustomerId == null) {
            throw new CallerIdentityRequiredException("Customer identification is required");
        }
        return persistNewOrder(request, effectiveCustomerId, OrderSource.CUSTOMER, null, correlationId);
    }

    /**
     * Creates an order for a merchant (called by the internal endpoint). It follows the same path as a customer
     * order, so pricing, tracking number, history and the {@code order.created} event are identical.
     * The merchant id is stored as the customer id, the convention the merchant service already uses.
     */
    @Transactional
    public CustomerOrderResponse createMerchantOrder(InternalCreateOrderRequest request, UUID correlationId) {
        return persistNewOrder(request.toCreateOrderRequest(), request.merchantId(), OrderSource.MERCHANT,
                request.externalRef(), correlationId);
    }

    private CustomerOrderResponse persistNewOrder(CreateOrderRequest request, UUID customerId, OrderSource source,
                                                  String externalRef, UUID correlationId) {
        requireAcceptedVolume(request.getLengthCm(), request.getWidthCm(), request.getHeightCm());
        OrderSpeed speed = request.getSpeed() != null ? request.getSpeed() : OrderSpeed.STANDARD;
        var deliverBy = deliveryPromise.deliverBy(speed, request.getPickupDistrict(), request.getDeliveryDistrict());

        // 1. Calculate price and chargeable weight
        FeeEstimateRequest estimateReq = FeeEstimateRequest.builder()
                .pickupDistrict(request.getPickupDistrict())
                .deliveryDistrict(request.getDeliveryDistrict())
                .actualWeightKg(request.getActualWeightKg())
                .lengthCm(request.getLengthCm())
                .widthCm(request.getWidthCm())
                .heightCm(request.getHeightCm())
                .speed(speed)
                .build();
        FeeEstimateResponse estimate = pricingClient.estimate(estimateReq);

        // 2. Generate non-sequential tracking number (SD-XXXX-XXXX)
        String trackingNumber = trackingNumberGenerator.generateUniqueTrackingNumber();

        // 3. Persist CustomerOrder entity
        CustomerOrder order = CustomerOrder.builder()
                .orderId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .customerId(customerId)
                // Only the merchant service creates a shop's order; a customer's booking never names a shop.
                .merchantId(source == OrderSource.MERCHANT ? request.getMerchantId() : null)
                .source(source)
                .externalRef(externalRef)
                .clientRequestId(source == OrderSource.CUSTOMER ? request.getClientRequestId() : null)
                .orderSpeed(estimate.getSpeed())
                .status(PublicOrderStatus.CREATED)
                .internalQueueStatus(InternalQueueStatus.CREATED)
                .pickupAddressLine1(request.getPickupAddressLine1())
                .pickupAddressLine2(request.getPickupAddressLine2())
                .pickupCity(request.getPickupCity())
                .pickupDistrict(request.getPickupDistrict())
                .pickupPostalCode(request.getPickupPostalCode())
                .pickupContactName(request.getPickupContactName())
                .pickupContactPhone(SriLankanPhone.toE164(request.getPickupContactPhone()))
                .pickupContactAlternatePhone(SriLankanPhone.toE164OrNull(request.getPickupContactAlternatePhone()))
                .deliveryAddressLine1(request.getDeliveryAddressLine1())
                .deliveryAddressLine2(request.getDeliveryAddressLine2())
                .deliveryCity(request.getDeliveryCity())
                .deliveryDistrict(request.getDeliveryDistrict())
                .deliveryPostalCode(request.getDeliveryPostalCode())
                .recipientName(request.getRecipientName())
                .recipientPhone(SriLankanPhone.toE164(request.getRecipientPhone()))
                .recipientAlternatePhone(SriLankanPhone.toE164OrNull(request.getRecipientAlternatePhone()))
                .recipientEmail(request.getRecipientEmail())
                .lengthCm(request.getLengthCm())
                .widthCm(request.getWidthCm())
                .heightCm(request.getHeightCm())
                .actualWeightKg(request.getActualWeightKg())
                .chargeableWeightKg(estimate.getChargeableWeightKg())
                .itemDescription(request.getItemDescription())
                .customerNotes(blankToNull(request.getCustomerNotes()))
                .declaredValue(request.getDeclaredValue() != null ? request.getDeclaredValue() : BigDecimal.ZERO)
                .prohibitedItemsDeclared(request.getProhibitedItemsDeclared())
                .pricingRuleVersionId(estimate.getPricingRuleVersionId())
                .estimatedFee(estimate.getEstimatedFee())
                .finalFee(estimate.getEstimatedFee())
                .currency("LKR")
                .feeRevisionStatus(FeeRevisionStatus.APPROVED)
                .deliverBy(deliverBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        CustomerOrder saved = customerOrderRepository.save(order);
        log.info("Created customer order orderId={}, trackingNumber={}", saved.getOrderId(), saved.getTrackingNumber());

        // 4. Publish order.created and parcel.status.changed events
        orderStatusHistoryService.recordCurrentStatus(saved, null);
        orderEventPublisher.publishOrderCreated(saved, correlationId);

        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse getOrderById(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return CustomerOrderResponse.fromEntity(order);
    }

    /** The statuses an order has been through, oldest first. Owner only (404 for anyone else), like the other reads. */
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryEntry> getStatusHistory(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return orderStatusHistoryService.listForOrder(order.getOrderId());
    }

    @Transactional(readOnly = true)
    public CustomerOrderResponse getOrderByTrackingNumber(String trackingNumber, UUID callerId, String callerRole) {
        CustomerOrder order = customerOrderRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        // The sender sees their own parcel in full on the tracking page; a shop sees its own orders.
        if (!isOwnMerchantOrder(order, callerRole)) {
            assertCallerCanAccess(order, callerId, callerRole);
        }
        return CustomerOrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public PagedResult<CustomerOrderResponse> listCustomerOrders(UUID requestedCustomerId, UUID callerId, String callerRole, Pageable pageable) {
        // Only dispatchers and admins may list another customer's orders; everyone else sees their own.
        UUID customerId = isPrivilegedRole(callerRole) && requestedCustomerId != null ? requestedCustomerId : callerId;
        if (customerId == null) {
            throw new CallerIdentityRequiredException("Customer identification is required");
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
     * Ownership rule shared by every order endpoint: a system admin may access any order, a dispatcher the
     * orders picked up in or bound for their district, everyone else only their own. Anyone else gets the
     * same 404 as a missing order so existence is not leaked.
     */
    private CustomerOrder findAccessibleOrder(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = customerOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        assertCallerCanAccess(order, callerId, callerRole);
        return order;
    }

    /** Same as {@link #findAccessibleOrder} but takes a row lock so it serialises with status sync. */
    private CustomerOrder findAccessibleOrderForUpdate(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = customerOrderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        assertCallerCanAccess(order, callerId, callerRole);
        return order;
    }

    /** A merchant's own shop order: placed through the merchant service for the caller's company. */
    private static boolean isOwnMerchantOrder(CustomerOrder order, String callerRole) {
        return UserRole.fromClaim(callerRole) == UserRole.MERCHANT
                && order.getSource() == OrderSource.MERCHANT
                && order.getMerchantId() != null && order.getMerchantId().equals(CallerMerchant.current());
    }

    private void assertCallerCanAccess(CustomerOrder order, UUID callerId, String callerRole) {
        UserRole role = UserRole.fromClaim(callerRole);
        if (role == UserRole.SYSTEM_ADMIN) {
            return;
        }
        if (role == UserRole.DISPATCHER) {
            SriLankanDistrict district = CallerDistrict.current();
            if (district != null && (district == order.getPickupDistrict() || district == order.getDeliveryDistrict())) {
                return;
            }
            throw new OrderNotFoundException("Order not found");
        }
        if (callerId == null || !callerId.equals(order.getCustomerId())) {
            throw new OrderNotFoundException("Order not found");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireAcceptedVolume(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        if (ParcelLimits.exceedsMaxVolume(lengthCm, widthCm, heightCm)) {
            throw new ParcelTooLargeException();
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
        if (request.getPickupContactPhone() != null) order.setPickupContactPhone(SriLankanPhone.toE164(request.getPickupContactPhone()));
        if (request.getPickupContactAlternatePhone() != null) order.setPickupContactAlternatePhone(SriLankanPhone.toE164OrNull(request.getPickupContactAlternatePhone()));

        if (request.getDeliveryAddressLine1() != null) order.setDeliveryAddressLine1(request.getDeliveryAddressLine1());
        if (request.getDeliveryAddressLine2() != null) order.setDeliveryAddressLine2(request.getDeliveryAddressLine2());
        if (request.getDeliveryCity() != null) order.setDeliveryCity(request.getDeliveryCity());
        if (request.getDeliveryDistrict() != null) order.setDeliveryDistrict(request.getDeliveryDistrict());
        if (request.getDeliveryPostalCode() != null) order.setDeliveryPostalCode(request.getDeliveryPostalCode());
        if (request.getRecipientName() != null) order.setRecipientName(request.getRecipientName());
        if (request.getRecipientPhone() != null) order.setRecipientPhone(SriLankanPhone.toE164(request.getRecipientPhone()));
        if (request.getRecipientAlternatePhone() != null) order.setRecipientAlternatePhone(SriLankanPhone.toE164OrNull(request.getRecipientAlternatePhone()));
        if (request.getRecipientEmail() != null) order.setRecipientEmail(request.getRecipientEmail());

        if (request.getItemDescription() != null) order.setItemDescription(request.getItemDescription());
        if (request.getCustomerNotes() != null) order.setCustomerNotes(blankToNull(request.getCustomerNotes()));
        if (request.getDeclaredValue() != null) order.setDeclaredValue(request.getDeclaredValue());

        boolean dimsOrSpeedChanged = false;
        if (request.getLengthCm() != null) { order.setLengthCm(request.getLengthCm()); dimsOrSpeedChanged = true; }
        if (request.getWidthCm() != null) { order.setWidthCm(request.getWidthCm()); dimsOrSpeedChanged = true; }
        if (request.getHeightCm() != null) { order.setHeightCm(request.getHeightCm()); dimsOrSpeedChanged = true; }
        if (request.getActualWeightKg() != null) { order.setActualWeightKg(request.getActualWeightKg()); dimsOrSpeedChanged = true; }
        if (request.getSpeed() != null) { order.setOrderSpeed(request.getSpeed()); dimsOrSpeedChanged = true; }
        // The promise follows the speed and the districts; Same day is checked again when either changes.
        if (request.getSpeed() != null || request.getPickupDistrict() != null || request.getDeliveryDistrict() != null) {
            order.setDeliverBy(deliveryPromise.deliverBy(order.getOrderSpeed(), order.getPickupDistrict(),
                    order.getDeliveryDistrict()));
        }

        if (dimsOrSpeedChanged) {
            requireAcceptedVolume(order.getLengthCm(), order.getWidthCm(), order.getHeightCm());
            FeeEstimateRequest estimateReq = FeeEstimateRequest.builder()
                    .pickupDistrict(order.getPickupDistrict())
                    .deliveryDistrict(order.getDeliveryDistrict())
                    .actualWeightKg(order.getActualWeightKg())
                    .lengthCm(order.getLengthCm())
                    .widthCm(order.getWidthCm())
                    .heightCm(order.getHeightCm())
                    .speed(order.getOrderSpeed())
                    .build();
            FeeEstimateResponse estimate = pricingClient.estimate(estimateReq);
            order.setChargeableWeightKg(estimate.getChargeableWeightKg());
            order.setEstimatedFee(estimate.getEstimatedFee());
            order.setFinalFee(estimate.getEstimatedFee());
        }

        order.setUpdatedAt(Instant.now());
        CustomerOrder saved = customerOrderRepository.save(order);
        if (dimsOrSpeedChanged || request.getPickupDistrict() != null || request.getDeliveryDistrict() != null) {
            orderEventPublisher.publishOrderUpdated(saved, null);
        }
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
        CustomerOrder order = findAccessibleOrderForUpdate(orderId, callerId, callerRole);

        if (order.getStatus().isTerminal()) {
            throw new InvalidOrderStateException("Order " + orderId + " is already in terminal state " + order.getStatus());
        }
        // A customer can cancel only a booked parcel that no courier has been assigned to. Dispatchers
        // and admins keep the wider right, because they have to stop parcels that are already moving.
        if (!isPrivilegedRole(callerRole) && order.getStatus() != PublicOrderStatus.CREATED) {
            throw new InvalidOrderStateException("Only a booked parcel with no courier assigned yet can be cancelled.");
        }

        PublicOrderStatus previousStatus = order.getStatus();
        order.setStatus(PublicOrderStatus.CANCELLED);
        order.setInternalQueueStatus(InternalQueueStatus.CANCELLED);
        order.setCancellationReason(request != null ? request.getReason() : "Customer cancelled order");
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        CustomerOrder saved = customerOrderRepository.save(order);
        orderStatusHistoryService.recordCurrentStatus(saved, null);
        orderEventPublisher.publishOrderCancelled(saved, previousStatus, order.getCancellationReason(), "CUSTOMER",
                correlationId);
        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional
    public CustomerOrderResponse acceptFeeRevision(UUID orderId, UUID callerId, String callerRole, UUID correlationId) {
        CustomerOrder order = findAccessibleOrderForUpdate(orderId, callerId, callerRole);

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
        CustomerOrder order = findAccessibleOrderForUpdate(orderId, callerId, callerRole);

        if (order.getFeeRevisionStatus() != FeeRevisionStatus.PENDING) {
            throw new InvalidOrderStateException("Order " + orderId + " does not have a pending fee revision.");
        }

        // FR-C8: Auto-cancelling at $0 if rejected
        order.setFinalFee(BigDecimal.ZERO);
        order.setFeeRevisionStatus(FeeRevisionStatus.REJECTED);
        PublicOrderStatus previousStatus = order.getStatus();
        order.setStatus(PublicOrderStatus.CANCELLED);
        order.setInternalQueueStatus(InternalQueueStatus.CANCELLED);
        order.setCancellationReason("Customer rejected courier fee revision of LKR " + order.getRevisedFee());
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        CustomerOrder saved = customerOrderRepository.save(order);
        orderStatusHistoryService.recordCurrentStatus(saved, null);
        orderEventPublisher.publishOrderCancelled(saved, previousStatus, order.getCancellationReason(), "CUSTOMER",
                correlationId);
        log.info("Customer rejected fee revision for order {}: cancelled at LKR 0.00", orderId);
        return CustomerOrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PdfDownload getShippingLabelPdf(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return new PdfDownload(order.getTrackingNumber(), orderPdfGeneratorService.generateShippingLabelAndReceiptPdf(order));
    }

    /** One label per page, in the order asked for. A merchant prints labels of their own company's orders. */
    @Transactional(readOnly = true)
    public byte[] getShippingLabelsPdf(List<UUID> orderIds, UUID callerId, String callerRole) {
        List<CustomerOrder> orders = orderIds.stream().distinct().map(orderId -> {
            CustomerOrder order = customerOrderRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found"));
            if (!isOwnMerchantOrder(order, callerRole)) {
                assertCallerCanAccess(order, callerId, callerRole);
            }
            return order;
        }).toList();
        return orderPdfGeneratorService.generateShippingLabelsPdf(orders);
    }

    @Transactional(readOnly = true)
    public PdfDownload getDeliveryReceiptPdf(UUID orderId, UUID callerId, String callerRole) {
        CustomerOrder order = findAccessibleOrder(orderId, callerId, callerRole);
        return new PdfDownload(order.getTrackingNumber(), orderPdfGeneratorService.generateDeliveryReceiptPdf(order));
    }

    /** A generated PDF together with the tracking number used to name the download. */
    public record PdfDownload(String trackingNumber, byte[] content) {

        /** Download name such as {@code receipt-SD-7K4M-9QX2.pdf}; only letters, digits and hyphens survive from the tracking number. */
        public String fileName(String prefix) {
            return prefix + "-" + trackingNumber.replaceAll("[^A-Za-z0-9-]", "") + ".pdf";
        }
    }
}
