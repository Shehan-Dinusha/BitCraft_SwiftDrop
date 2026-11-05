package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderSource;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.dto.InternalCreateOrderRequest;
import com.swiftdrop.order.exception.OrderNotFoundException;
import com.swiftdrop.order.exception.PricingUnavailableException;
import com.swiftdrop.order.messaging.publisher.OrderEventPublisher;
import com.swiftdrop.order.service.pricing.PricingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Merchant order creation: the shared create path, and idempotency on (merchantId, externalRef). */
@ExtendWith(MockitoExtension.class)
class InternalMerchantOrderTest {

    private static final UUID MERCHANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String EXTERNAL_REF = "SHOP-2026-000123";

    @Mock
    private CustomerOrderRepository customerOrderRepository;
    @Mock
    private TrackingNumberGenerator trackingNumberGenerator;
    @Mock
    private PricingClient pricingClient;
    @Mock
    private OrderPdfGeneratorService orderPdfGeneratorService;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private OrderStatusHistoryService orderStatusHistoryService;

    @InjectMocks
    private CustomerOrderService customerOrderService;

    private InternalCreateOrderRequest request;

    @BeforeEach
    void setUp() {
        request = new InternalCreateOrderRequest(MERCHANT_ID, EXTERNAL_REF,
                "No. 42, Galle Road", null, "Colombo 03", SriLankanDistrict.COLOMBO, "00300", "Sunil Perera", "+94771234567",
                "No. 18, Peradeniya Road", null, "Kandy", SriLankanDistrict.KANDY, "20000", "Kasun Perera", "0711234567",
                null, null, null, null, new BigDecimal("2.4"), OrderSpeed.EXPRESS, "Batik sarees", null, null, true);
    }

    private FeeEstimateResponse pricedAt(String fee) {
        return FeeEstimateResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .speed(OrderSpeed.EXPRESS)
                .chargeableWeightKg(new BigDecimal("2.40"))
                .estimatedFee(new BigDecimal(fee))
                .pricingRuleVersionId("PRICING-TEST-V9")
                .currency("LKR")
                .build();
    }

    private CustomerOrderResponse createWithMocks() {
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn("SD-7K4M-9QX2");
        when(pricingClient.estimate(any(FeeEstimateRequest.class))).thenReturn(pricedAt("1234.50"));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        return customerOrderService.createMerchantOrder(request, UUID.randomUUID());
    }

    @Test
    @DisplayName("A merchant order is a MERCHANT order owned by the merchant, with the fee from the pricing client")
    void createsMerchantOrderWithPricedFee() {
        CustomerOrderResponse response = createWithMocks();

        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(customerOrderRepository).save(saved.capture());
        CustomerOrder order = saved.getValue();
        assertThat(order.getSource()).isEqualTo(OrderSource.MERCHANT);
        assertThat(order.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(order.getCustomerId()).as("customer id follows the merchant convention").isEqualTo(MERCHANT_ID);
        assertThat(order.getExternalRef()).isEqualTo(EXTERNAL_REF);
        assertThat(order.getStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(order.getEstimatedFee()).isEqualByComparingTo("1234.50");
        assertThat(order.getChargeableWeightKg()).isEqualByComparingTo("2.40");
        assertThat(order.getPricingRuleVersionId()).isEqualTo("PRICING-TEST-V9");
        assertThat(order.getRecipientEmail()).as("recipient email is optional for merchant orders").isNull();
        assertThat(response.getTrackingNumber()).isEqualTo("SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("The price comes only from the pricing client, with the default dimensions applied")
    void pricesThroughClientWithDefaultDimensions() {
        createWithMocks();

        ArgumentCaptor<FeeEstimateRequest> priced = ArgumentCaptor.forClass(FeeEstimateRequest.class);
        verify(pricingClient).estimate(priced.capture());
        assertThat(priced.getValue().getLengthCm()).isEqualByComparingTo("10");
        assertThat(priced.getValue().getWidthCm()).isEqualByComparingTo("10");
        assertThat(priced.getValue().getHeightCm()).isEqualByComparingTo("10");
        assertThat(priced.getValue().getActualWeightKg()).isEqualByComparingTo("2.4");
        assertThat(priced.getValue().getSpeed()).isEqualTo(OrderSpeed.EXPRESS);
    }

    @Test
    @DisplayName("Creation writes the CREATED history row and publishes order.created once, through the shared publisher")
    void writesHistoryAndPublishesOnce() {
        UUID correlationId = UUID.randomUUID();
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn("SD-7K4M-9QX2");
        when(pricingClient.estimate(any(FeeEstimateRequest.class))).thenReturn(pricedAt("100.00"));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        customerOrderService.createMerchantOrder(request, correlationId);

        verify(orderStatusHistoryService).recordCurrentStatus(any(CustomerOrder.class), isNull());
        verify(orderEventPublisher, times(1)).publishOrderCreated(any(CustomerOrder.class), eq(correlationId));
    }

    @Test
    @DisplayName("When pricing is unavailable nothing is saved or published")
    void pricingUnavailableSavesAndPublishesNothing() {
        when(pricingClient.estimate(any(FeeEstimateRequest.class))).thenThrow(new PricingUnavailableException());

        assertThatThrownBy(() -> customerOrderService.createMerchantOrder(request, UUID.randomUUID()))
                .isInstanceOf(PricingUnavailableException.class);

        verify(customerOrderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher, orderStatusHistoryService);
    }

    // ---- idempotency (InternalOrderService) ----

    @Mock
    private CustomerOrderService mockedOrderService;

    private InternalOrderService idempotentService() {
        return new InternalOrderService(mockedOrderService, customerOrderRepository);
    }

    private CustomerOrder existingOrder() {
        return CustomerOrder.builder()
                .id(7L).orderId(UUID.randomUUID()).trackingNumber("SD-AAAA-BBBB")
                .customerId(MERCHANT_ID).merchantId(MERCHANT_ID).source(OrderSource.MERCHANT).externalRef(EXTERNAL_REF)
                .status(PublicOrderStatus.CREATED).estimatedFee(new BigDecimal("99.00")).finalFee(new BigDecimal("99.00"))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("The first request creates the order")
    void firstRequestCreates() {
        CustomerOrderResponse created = CustomerOrderResponse.builder().trackingNumber("SD-NEW1-NEW1").build();
        when(customerOrderRepository.findByMerchantIdAndExternalRef(MERCHANT_ID, EXTERNAL_REF)).thenReturn(Optional.empty());
        when(mockedOrderService.createMerchantOrder(any(), any())).thenReturn(created);

        InternalOrderService.CreateResult result = idempotentService().createMerchantOrder(request, null);

        assertThat(result.created()).isTrue();
        assertThat(result.order()).isSameAs(created);
    }

    @Test
    @DisplayName("Posting the same (merchantId, externalRef) again returns the same order and creates nothing")
    void repeatReturnsSameOrderWithoutCreating() {
        CustomerOrder existing = existingOrder();
        when(customerOrderRepository.findByMerchantIdAndExternalRef(MERCHANT_ID, EXTERNAL_REF)).thenReturn(Optional.of(existing));

        InternalOrderService.CreateResult result = idempotentService().createMerchantOrder(request, UUID.randomUUID());

        assertThat(result.created()).isFalse();
        assertThat(result.order().getOrderId()).isEqualTo(existing.getOrderId());
        assertThat(result.order().getTrackingNumber()).isEqualTo("SD-AAAA-BBBB");
        verify(mockedOrderService, never()).createMerchantOrder(any(), any());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    @DisplayName("A concurrent duplicate that loses the unique-index race re-reads and returns the winner")
    void raceLoserReturnsWinner() {
        CustomerOrder winner = existingOrder();
        when(customerOrderRepository.findByMerchantIdAndExternalRef(MERCHANT_ID, EXTERNAL_REF))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(mockedOrderService.createMerchantOrder(any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        InternalOrderService.CreateResult result = idempotentService().createMerchantOrder(request, UUID.randomUUID());

        assertThat(result.created()).isFalse();
        assertThat(result.order().getOrderId()).isEqualTo(winner.getOrderId());
    }

    @Test
    @DisplayName("An integrity error that is not a duplicate of this reference is rethrown")
    void unrelatedIntegrityErrorIsRethrown() {
        when(customerOrderRepository.findByMerchantIdAndExternalRef(MERCHANT_ID, EXTERNAL_REF)).thenReturn(Optional.empty());
        when(mockedOrderService.createMerchantOrder(any(), any()))
                .thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> idempotentService().createMerchantOrder(request, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Pricing outage from the create step is not swallowed by the idempotency layer")
    void pricingOutagePropagates() {
        when(customerOrderRepository.findByMerchantIdAndExternalRef(MERCHANT_ID, EXTERNAL_REF)).thenReturn(Optional.empty());
        when(mockedOrderService.createMerchantOrder(any(), any())).thenThrow(new PricingUnavailableException());

        assertThatThrownBy(() -> idempotentService().createMerchantOrder(request, null))
                .isInstanceOf(PricingUnavailableException.class);
    }

    // ---- visibility through the public endpoints ----

    @Test
    @DisplayName("A merchant order is readable by its merchant (X-User-Id = merchant id) and not by another customer")
    void merchantOrderIsNotVisibleToOtherCustomers() {
        CustomerOrder merchantOrder = existingOrder();
        when(customerOrderRepository.findByOrderId(merchantOrder.getOrderId())).thenReturn(Optional.of(merchantOrder));

        assertThat(customerOrderService.getOrderById(merchantOrder.getOrderId(), MERCHANT_ID, "MERCHANT").getOrderId())
                .isEqualTo(merchantOrder.getOrderId());
        assertThatThrownBy(() -> customerOrderService.getOrderById(merchantOrder.getOrderId(), UUID.randomUUID(), "CUSTOMER"))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> customerOrderService.getOrderById(merchantOrder.getOrderId(), UUID.randomUUID(), "MERCHANT"))
                .isInstanceOf(OrderNotFoundException.class);
        verify(customerOrderRepository, never()).findByTrackingNumber(anyString());
    }
}
