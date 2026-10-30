package com.swiftdrop.order.service;

import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.exception.OrderNotFoundException;
import com.swiftdrop.order.messaging.publisher.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOwnershipTest {

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

    private UUID customerA;
    private UUID customerB;
    private UUID orderId;
    private CustomerOrder orderOfA;

    @BeforeEach
    void setUp() {
        customerA = UUID.randomUUID();
        customerB = UUID.randomUUID();
        orderId = UUID.randomUUID();

        orderOfA = CustomerOrder.builder()
                .id(1L)
                .orderId(orderId)
                .trackingNumber("SD-7K4M-9QX2")
                .customerId(customerA)
                .orderSpeed(OrderSpeed.STANDARD)
                .status(PublicOrderStatus.CREATED)
                .internalQueueStatus(InternalQueueStatus.CREATED)
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
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

    private void orderExists() {
        when(customerOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(orderOfA));
    }

    @Test
    @DisplayName("Owner can read their own order")
    void ownerCanReadOwnOrder() {
        orderExists();

        CustomerOrderResponse response = customerOrderService.getOrderById(orderId, customerA, "CUSTOMER");

        assertThat(response.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Customer B gets 404 (not found) on customer A's order")
    void otherCustomerGetsNotFound() {
        orderExists();

        assertThatThrownBy(() -> customerOrderService.getOrderById(orderId, customerB, "CUSTOMER"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Missing, blank or unknown role is treated as a non-privileged caller")
    void missingRoleIsNotPrivileged() {
        orderExists();

        assertThatThrownBy(() -> customerOrderService.getOrderById(orderId, customerB, null))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> customerOrderService.getOrderById(orderId, customerB, " "))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> customerOrderService.getOrderById(orderId, customerB, "NOT_A_ROLE"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Missing caller id is refused with 404 for a non-privileged caller")
    void missingCallerIdIsNotAllowed() {
        orderExists();

        assertThatThrownBy(() -> customerOrderService.getOrderById(orderId, null, "CUSTOMER"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Not-found message is identical for a missing order and a foreign order")
    void notFoundMessageDoesNotLeakExistence() {
        orderExists();
        UUID missingId = UUID.randomUUID();
        when(customerOrderRepository.findByOrderId(missingId)).thenReturn(Optional.empty());

        Throwable foreign = org.assertj.core.api.Assertions.catchThrowable(
                () -> customerOrderService.getOrderById(orderId, customerB, "CUSTOMER"));
        Throwable missing = org.assertj.core.api.Assertions.catchThrowable(
                () -> customerOrderService.getOrderById(missingId, customerB, "CUSTOMER"));

        assertThat(foreign.getMessage()).isEqualTo("Order not found with ID: " + orderId);
        assertThat(missing.getMessage()).isEqualTo("Order not found with ID: " + missingId);
    }

    @Test
    @DisplayName("Dispatcher and system admin can read any order")
    void privilegedRolesCanReadAnyOrder() {
        orderExists();

        assertThat(customerOrderService.getOrderById(orderId, customerB, "DISPATCHER").getOrderId()).isEqualTo(orderId);
        assertThat(customerOrderService.getOrderById(orderId, customerB, "SYSTEM_ADMIN").getOrderId()).isEqualTo(orderId);
        assertThat(customerOrderService.getOrderById(orderId, null, "dispatcher").getOrderId()).isEqualTo(orderId);
        assertThat(customerOrderService.getOrderById(orderId, customerB, "ADMIN").getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Customer list is filtered by the caller, and a foreign customerId is ignored")
    void customerListIsFilteredByCaller() {
        Pageable pageable = PageRequest.of(0, 10);
        when(customerOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerB, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PagedResult<CustomerOrderResponse> result =
                customerOrderService.listCustomerOrders(customerA, customerB, "CUSTOMER", pageable);

        verify(customerOrderRepository).findByCustomerIdOrderByCreatedAtDesc(customerB, pageable);
        verify(customerOrderRepository, never()).findByCustomerIdOrderByCreatedAtDesc(customerA, pageable);
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("Dispatcher can list another customer's orders")
    void dispatcherCanListAnyCustomer() {
        Pageable pageable = PageRequest.of(0, 10);
        when(customerOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerA, pageable))
                .thenReturn(new PageImpl<>(List.of(orderOfA), pageable, 1));

        PagedResult<CustomerOrderResponse> result =
                customerOrderService.listCustomerOrders(customerA, customerB, "DISPATCHER", pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("Listing without any caller identity is rejected")
    void listWithoutIdentityIsRejected() {
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> customerOrderService.listCustomerOrders(customerA, null, "CUSTOMER", pageable))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> customerOrderService.listCustomerOrders(null, null, null, pageable))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
