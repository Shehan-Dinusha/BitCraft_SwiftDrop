package com.swiftdrop.order.config;

import com.swiftdrop.order.service.promise.DeliveryPromise;
import com.swiftdrop.order.service.promise.HttpClosedDaysClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** The clock and the dispatch calendar the delivery promise reads. */
@Configuration
public class DispatchCalendarConfig {

    @Bean
    public Clock clock() {
        return Clock.system(DeliveryPromise.ZONE);
    }

    @Bean
    public HttpClosedDaysClient httpClosedDaysClient(RestClient.Builder builder,
                                                     @Value("${dispatch.service.url}") String baseUrl) {
        return new HttpClosedDaysClient(PricingClientConfig.buildRestClient(builder, baseUrl, 1000, 3000));
    }
}
