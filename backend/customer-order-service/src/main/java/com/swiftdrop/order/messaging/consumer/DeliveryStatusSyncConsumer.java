package com.swiftdrop.order.messaging.consumer;

import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.events.delivery.ParcelFeeRevisedEvent;
import com.swiftdrop.order.config.RabbitMQConfig;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumes courier fee-revision requests (FR-C8, FR-K5) sent when a courier
 * discovers a weight/dimension discrepancy during pickup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryStatusSyncConsumer {

    private final CustomerOrderRepository customerOrderRepository;

    @RabbitListener(queues = RabbitMQConfig.FEE_REVISION_QUEUE)
    @Transactional
    public void onParcelFeeRevised(ParcelFeeRevisedEvent event) {
        log.info("Received ParcelFeeRevisedEvent for trackingNumber={}, orderId={}, revisedFee={}",
                event.trackingNumber(), event.orderId(), event.revisedFee());

        Optional<CustomerOrder> orderOpt = Optional.empty();
        if (event.orderId() != null) {
            orderOpt = customerOrderRepository.findByOrderId(event.orderId());
        }
        if (orderOpt.isEmpty() && event.trackingNumber() != null) {
            orderOpt = customerOrderRepository.findByTrackingNumber(event.trackingNumber());
        }

        if (orderOpt.isPresent()) {
            CustomerOrder order = orderOpt.get();
            order.setRevisedFee(event.revisedFee());
            order.setFeeRevisionReason(event.reason());
            order.setFeeRevisionStatus(FeeRevisionStatus.PENDING);
            order.setUpdatedAt(Instant.now());
            customerOrderRepository.save(order);
            log.info("Order {} updated with PENDING fee revision of LKR {}", order.getOrderId(), event.revisedFee());
        } else {
            log.warn("Could not find order for fee revision event: orderId={}, trackingNumber={}",
                    event.orderId(), event.trackingNumber());
        }
    }
}
