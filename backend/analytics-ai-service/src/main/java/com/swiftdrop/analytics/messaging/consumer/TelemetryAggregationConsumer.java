package com.swiftdrop.analytics.messaging.consumer;

import com.swiftdrop.analytics.config.RabbitMQConfig;
import com.swiftdrop.analytics.messaging.TelemetryEventDecoder;
import com.swiftdrop.analytics.service.TelemetryProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
public class TelemetryAggregationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAggregationConsumer.class);

    private final TelemetryEventDecoder decoder;
    private final TelemetryProcessingService processing;

    public TelemetryAggregationConsumer(TelemetryEventDecoder decoder, TelemetryProcessingService processing) {
        this.decoder = decoder;
        this.processing = processing;
    }

    @RabbitListener(id = "analyticsTelemetry", queues = RabbitMQConfig.ANALYTICS_EVENTS_QUEUE,
            containerFactory = RabbitMQConfig.ANALYTICS_LISTENER_FACTORY)
    public void processMessage(Message message) {
        var event = decoder.decode(message);
        if (event.isPresent()) {
            processing.process(event.get());
        } else {
            // dispatch.# includes events not used by these projections.
            log.debug("Ignoring unsupported analytics routing key {}",
                    message.getMessageProperties().getReceivedRoutingKey());
        }
    }
}
