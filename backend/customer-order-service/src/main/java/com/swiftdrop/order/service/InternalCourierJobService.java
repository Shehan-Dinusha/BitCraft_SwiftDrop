package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.InternalCourierJobView;
import com.swiftdrop.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalCourierJobService {

    private final CustomerOrderRepository customerOrderRepository;

    @Transactional(readOnly = true)
    public InternalCourierJobView getCourierJob(UUID orderId) {
        CustomerOrder order = customerOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        return new InternalCourierJobView(
                order.getOrderId(),
                order.getStatus() == PublicOrderStatus.CANCELLED,
                order.getOrderSpeed(),
                new InternalCourierJobView.Stop(
                        order.getPickupContactName(),
                        order.getPickupContactPhone(),
                        order.getPickupContactAlternatePhone(),
                        order.getPickupAddressLine1(),
                        order.getPickupAddressLine2(),
                        order.getPickupCity(),
                        order.getPickupPostalCode(),
                        order.getPickupDistrict()),
                new InternalCourierJobView.Stop(
                        order.getRecipientName(),
                        order.getRecipientPhone(),
                        order.getRecipientAlternatePhone(),
                        order.getDeliveryAddressLine1(),
                        order.getDeliveryAddressLine2(),
                        order.getDeliveryCity(),
                        order.getDeliveryPostalCode(),
                        order.getDeliveryDistrict()),
                order.getCustomerNotes(),
                order.getItemDescription(),
                order.getLengthCm(),
                order.getWidthCm(),
                order.getHeightCm(),
                order.getActualWeightKg(),
                order.getFinalFee(),
                order.getCurrency());
    }
}
