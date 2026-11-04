package com.swiftdrop.gateway.trackinghub.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.events.delivery.ParcelFeeRevisedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.config.RabbitMQConfig;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumes delivery and fee events for the Real-Time Tracking Hub.
 *
 * <p>The listener accepts raw {@link Message} instances and performs explicit
 * routing so location-only events are ignored and every public view is keyed by
 * the {@code parcel.status.changed} tracking number.</p>
 */
@Slf4j
@Component
public class ParcelStatusEventConsumer {

    private final TrackingBroadcastHubService broadcastHub;
    private final ObjectMapper objectMapper;

    public ParcelStatusEventConsumer(TrackingBroadcastHubService broadcastHub, ObjectMapper objectMapper) {
        this.broadcastHub = broadcastHub;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.TRACKING_QUEUE)
    public void onTrackingMessage(Message message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            if (payload.has("latitude") || payload.has("longitude")) {
                log.debug("Ignoring location-only tracking event; public views use status milestones");
            } else {
                broadcastHub.onStatusChanged(
                        objectMapper.treeToValue(payload, ParcelStatusChangedEvent.class));
            }
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Unable to process tracking event with routing key {}: {}",
                    message.getMessageProperties().getReceivedRoutingKey(), exception.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.FEE_QUEUE)
    public void onFeeMessage(Message message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            broadcastHub.onFeeRevised(objectMapper.treeToValue(payload, ParcelFeeRevisedEvent.class));
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Unable to process fee event with routing key {}: {}",
                    message.getMessageProperties().getReceivedRoutingKey(), exception.getMessage());
        }
    }
}
