package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CancelOrderRequest;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.dto.UpdateOrderRequest;
import com.swiftdrop.order.exception.InvalidOrderStateException;
import com.swiftdrop.order.messaging.publisher.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceTest {

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private TrackingNumberGenerator trackingNumberGenerator;

    @Mock
    private PricingCalculationService pricingCalculationService;

    @Mock
    private OrderPdfGeneratorService orderPdfGeneratorService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private CustomerOrderService customerOrderService;

    private UUID customerId;
    private UUID orderId;
    private CustomerOrder sampleOrder;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        sampleOrder = CustomerOrder.builder()
                .id(1L)
                .orderId(orderId)
                .trackingNumber("SD-7K4M-9QX2")
                .customerId(customerId)
                .orderSpeed(OrderSpeed.STANDARD)
                .status(PublicOrderStatus.CREATED)
                .internalQueueStatus(InternalQueueStatus.CREATED)
                .pickupAddressLine1("No. 1, Main St")
                .pickupCity("Colombo")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00100")
                .pickupContactName("Kamal")
                .pickupContactPhone("+94771234567")
                .deliveryAddressLine1("No. 2, Cross St")
                .deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20000")
                .recipientName("Nimal")
                .recipientPhone("+94711234567")
                .recipientEmail("nimal@gmail.com")
                .lengthCm(new BigDecimal("20.0"))
                .widthCm(new BigDecimal("15.0"))
                .heightCm(new BigDecimal("10.0"))
                .actualWeightKg(new BigDecimal("1.50"))
                .chargeableWeightKg(new BigDecimal("1.50"))
                .prohibitedItemsDeclared(true)
                .pricingRuleVersionId("PRICING-2026-V1")
                .estimatedFee(new BigDecimal("610.00"))
                .finalFee(new BigDecimal("610.00"))
                .currency("LKR")
                .feeRevisionStatus(FeeRevisionStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Create customer order saves entity and publishes order.created event")
    void testCreateOrder() {
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn("SD-7K4M-9QX2");

        FeeEstimateResponse mockEstimate = FeeEstimateResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .chargeableWeightKg(new BigDecimal("1.50"))
                .speed(OrderSpeed.STANDARD)
                .estimatedFee(new BigDecimal("610.00"))
                .pricingRuleVersionId("PRICING-2026-V1")
                .currency("LKR")
                .build();

        when(pricingCalculationService.calculateEstimate(any(FeeEstimateRequest.class)))
                .thenReturn(mockEstimate);

        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .pickupAddressLine1("No. 1, Main St")
                .pickupCity("Colombo")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00100")
                .pickupContactName("Kamal")
                .pickupContactPhone("+94771234567")
                .deliveryAddressLine1("No. 2, Cross St")
                .deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20000")
                .recipientName("Nimal")
                .recipientPhone("+94711234567")
                .recipientEmail("nimal@gmail.com")
                .lengthCm(new BigDecimal("20.0"))
                .widthCm(new BigDecimal("15.0"))
                .heightCm(new BigDecimal("10.0"))
                .actualWeightKg(new BigDecimal("1.50"))
                .speed(OrderSpeed.STANDARD)
                .prohibitedItemsDeclared(true)
                .build();

        UUID correlationId = UUID.randomUUID();
        CustomerOrderResponse response = customerOrderService.createOrder(request, customerId, correlationId);

        assertThat(response).isNotNull();
        assertThat(response.getTrackingNumber()).isEqualTo("SD-7K4M-9QX2");
        assertThat(response.getStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(response.getEstimatedFee()).isEqualByComparingTo("610.00");
        assertThat(response.getFinalFee()).isEqualByComparingTo("610.00");

        verify(orderEventPublisher).publishOrderCreated(any(CustomerOrder.class), eq(correlationId));
    }

    @Test
    @DisplayName("Update order succeeds while in CREATED status")
    void testUpdateOrderSuccess() {
        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(sampleOrder));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderRequest updateReq = UpdateOrderRequest.builder()
                .recipientName("Sunil Perera")
                .recipientPhone("+94779998877")
                .build();

        CustomerOrderResponse response = customerOrderService.updateOrder(orderId, updateReq, customerId);

        assertThat(response.getRecipientName()).isEqualTo("Sunil Perera");
        assertThat(response.getRecipientPhone()).isEqualTo("+94779998877");
    }

    @Test
    @DisplayName("Update order fails if order has transitioned beyond CREATED")
    void testUpdateOrderFailsWhenNotCreated() {
        sampleOrder.setStatus(PublicOrderStatus.PICKUP_ASSIGNED);
        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(sampleOrder));

        UpdateOrderRequest updateReq = UpdateOrderRequest.builder().recipientName("Sunil").build();

        assertThatThrownBy(() -> customerOrderService.updateOrder(orderId, updateReq, customerId))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("cannot be updated once it leaves CREATED status");
    }

    @Test
    @DisplayName("Delete order succeeds only in CREATED status")
    void testDeleteOrder() {
        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(sampleOrder));

        customerOrderService.deleteOrder(orderId, customerId);

        verify(customerOrderRepository).delete(sampleOrder);
    }

    @Test
    @DisplayName("Cancel order sets status CANCELLED and publishes event")
    void testCancelOrder() {
        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(sampleOrder));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelOrderRequest cancelReq = CancelOrderRequest.builder().reason("No longer needed").build();
        UUID correlationId = UUID.randomUUID();

        CustomerOrderResponse response = customerOrderService.cancelOrder(orderId, cancelReq, customerId, correlationId);

        assertThat(response.getStatus()).isEqualTo(PublicOrderStatus.CANCELLED);
        assertThat(response.getCancellationReason()).isEqualTo("No longer needed");
        verify(orderEventPublisher).publishOrderCancelled(any(CustomerOrder.class), eq("No longer needed"), eq("CUSTOMER"), eq(correlationId));
    }

    @Test
    @DisplayName("Customer accepts courier fee revision (FR-C8)")
    void testAcceptFeeRevision() {
        sampleOrder.setFeeRevisionStatus(FeeRevisionStatus.PENDING);
        sampleOrder.setRevisedFee(new BigDecimal("780.00"));
        sampleOrder.setFeeRevisionReason("Actual weight was 2.8kg instead of 1.5kg");

        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(sampleOrder));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID correlationId = UUID.randomUUID();
        CustomerOrderResponse response = customerOrderService.acceptFeeRevision(orderId, customerId, correlationId);

        assertThat(response.getFeeRevisionStatus()).isEqualTo(FeeRevisionStatus.APPROVED);
        assertThat(response.getFinalFee()).isEqualByComparingTo("780.00");
    }

    @Test
    @DisplayName("Customer rejects courier fee revision and auto-cancels at $0 (FR-C8, FR-K5)")
    void testRejectFeeRevision() {
        sampleOrder.setFeeRevisionStatus(FeeRevisionStatus.PENDING);
        sampleOrder.setRevisedFee(new BigDecimal("950.00"));
        sampleOrder.setFeeRevisionReason("Package exceeded standard box limit");

        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(sampleOrder));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID correlationId = UUID.randomUUID();
        CustomerOrderResponse response = customerOrderService.rejectFeeRevision(orderId, customerId, correlationId);

        assertThat(response.getFeeRevisionStatus()).isEqualTo(FeeRevisionStatus.REJECTED);
        assertThat(response.getFinalFee()).isEqualByComparingTo("0.00");
        assertThat(response.getStatus()).isEqualTo(PublicOrderStatus.CANCELLED);
        assertThat(response.getInternalQueueStatus()).isEqualTo(InternalQueueStatus.CANCELLED);

        verify(orderEventPublisher).publishOrderCancelled(any(CustomerOrder.class), any(), eq("CUSTOMER"), eq(correlationId));
    }
}
