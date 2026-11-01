package com.swiftdrop.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import com.swiftdrop.order.dto.InternalTrackingView;
import com.swiftdrop.order.exception.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalTrackingServiceTest {

    private static final String TRACKING = "SD-7K4M-9QX2";

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @InjectMocks
    private InternalTrackingService internalTrackingService;

    private UUID orderId;
    private CustomerOrder order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = CustomerOrder.builder()
                .id(1L)
                .orderId(orderId)
                .trackingNumber(TRACKING)
                .customerId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .merchantId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .orderSpeed(OrderSpeed.EXPRESS)
                .status(PublicOrderStatus.IN_TRANSIT)
                .internalQueueStatus(InternalQueueStatus.CREATED)
                .pickupAddressLine1("77 Hidden Lane")
                .pickupAddressLine2("Flat 9B")
                .pickupCity("Colombo 03")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00300")
                .pickupContactName("Secret Sender")
                .pickupContactPhone("0771112223")
                .deliveryAddressLine1("12 Private Road")
                .deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20000")
                .recipientName("Secret Receiver")
                .recipientPhone("0714445556")
                .recipientEmail("secret.receiver@example.com")
                .lengthCm(new BigDecimal("30"))
                .widthCm(new BigDecimal("20"))
                .heightCm(new BigDecimal("15"))
                .actualWeightKg(new BigDecimal("2.4"))
                .chargeableWeightKg(new BigDecimal("2.4"))
                .itemDescription("Hidden contents")
                .prohibitedItemsDeclared(true)
                .pricingRuleVersionId("PRICING-2026-V1")
                .estimatedFee(new BigDecimal("1185.00"))
                .finalFee(new BigDecimal("1185.00"))
                .currency("LKR")
                .feeRevisionStatus(FeeRevisionStatus.APPROVED)
                .createdAt(Instant.parse("2026-10-30T08:00:00Z"))
                .updatedAt(Instant.parse("2026-10-30T12:00:00Z"))
                .build();
    }

    private OrderStatusHistory row(long id, PublicOrderStatus status, String at) {
        return OrderStatusHistory.builder().id(id).orderId(orderId).status(status).occurredAt(Instant.parse(at)).build();
    }

    @Test
    @DisplayName("The serialised view contains only public-safe fields, never address lines, names, phones, email, ids or fees")
    void viewContainsNoPersonalData() throws Exception {
        when(customerOrderRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByOccurredAtAscIdAsc(orderId)).thenReturn(List.of(
                row(1, PublicOrderStatus.CREATED, "2026-10-30T08:00:00Z")));

        InternalTrackingView view = internalTrackingService.getTrackingView(TRACKING);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(view);

        assertThat(json)
                .contains(TRACKING, "IN_TRANSIT", "Colombo 03", "COLOMBO", "Kandy", "KANDY")
                .doesNotContain("Hidden Lane", "Flat 9B", "Private Road", "00300", "20000")
                .doesNotContain("Secret Sender", "Secret Receiver", "0771112223", "0714445556", "secret.receiver")
                .doesNotContain("11111111-1111", "22222222-2222", "1185", "LKR", "PRICING-2026", "Hidden contents")
                .doesNotContain("customerId", "merchantId", "estimatedFee", "recipient", "phone", "email", "internal");
    }

    @Test
    @DisplayName("History is returned oldest to newest even if the repository returns it out of order")
    void historyIsOrderedOldestFirst() {
        when(customerOrderRepository.findByTrackingNumber(TRACKING)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByOccurredAtAscIdAsc(orderId)).thenReturn(List.of(
                row(3, PublicOrderStatus.IN_TRANSIT, "2026-10-30T12:00:00Z"),
                row(1, PublicOrderStatus.CREATED, "2026-10-30T08:00:00Z"),
                row(2, PublicOrderStatus.PICKED_UP, "2026-10-30T10:00:00Z")));

        InternalTrackingView view = internalTrackingService.getTrackingView(TRACKING);

        assertThat(view.history()).extracting(InternalTrackingView.HistoryEntry::status)
                .containsExactly(PublicOrderStatus.CREATED, PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT);
        assertThat(view.status()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("An unknown tracking number is a not-found error")
    void unknownTrackingNumberIsNotFound() {
        when(customerOrderRepository.findByTrackingNumber("SD-0000-0000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> internalTrackingService.getTrackingView("SD-0000-0000"))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
