package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.InternalCourierJobView;
import com.swiftdrop.order.exception.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalCourierJobServiceTest {

    @Mock
    private CustomerOrderRepository repository;

    @InjectMocks
    private InternalCourierJobService service;

    private final UUID orderId = UUID.randomUUID();

    @Test
    @DisplayName("Gives the fee the sender owes now (the final fee), not the first estimate")
    void givesTheFinalFee() {
        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(order(PublicOrderStatus.CREATED)));

        InternalCourierJobView job = service.getCourierJob(orderId);

        assertThat(job.fee()).isEqualByComparingTo("750.00");
        assertThat(job.codAmount()).isEqualByComparingTo("2500.00");
        assertThat(job.cancelled()).isFalse();
        assertThat(job.pickup().name()).isEqualTo("Nimal");
        assertThat(job.pickup().district()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(job.dropOff().name()).isEqualTo("Kamala");
        assertThat(job.dropOff().district()).isEqualTo(SriLankanDistrict.KANDY);
    }

    @Test
    @DisplayName("Says when the order was cancelled")
    void saysWhenCancelled() {
        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(order(PublicOrderStatus.CANCELLED)));

        assertThat(service.getCourierJob(orderId).cancelled()).isTrue();
    }

    @Test
    @DisplayName("An unknown order is not found")
    void unknownOrder() {
        when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCourierJob(orderId)).isInstanceOf(OrderNotFoundException.class);
    }

    private CustomerOrder order(PublicOrderStatus status) {
        return CustomerOrder.builder()
                .orderId(orderId)
                .status(status)
                .pickupContactName("Nimal")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .recipientName("Kamala")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .estimatedFee(new BigDecimal("670.00"))
                .finalFee(new BigDecimal("750.00"))
                .codAmount(new BigDecimal("2500.00"))
                .build();
    }
}
