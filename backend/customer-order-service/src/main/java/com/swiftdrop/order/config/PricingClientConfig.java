package com.swiftdrop.order.config;

import com.swiftdrop.order.service.pricing.HttpPricingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the pricing client. {@link HttpPricingClient} is the only implementation: the pricing service is the
 * single source of prices, so there is no local copy to fall back to.
 */
@Configuration
public class PricingClientConfig {

    @Bean
    public HttpPricingClient httpPricingClient(
            RestClient.Builder builder,
            @Value("${pricing.service.url}") String baseUrl,
            @Value("${pricing.service.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${pricing.service.read-timeout-ms}") long readTimeoutMs) {
        return new HttpPricingClient(buildRestClient(builder, baseUrl, connectTimeoutMs, readTimeoutMs));
    }

    public static RestClient buildRestClient(RestClient.Builder builder, String baseUrl,
                                             long connectTimeoutMs, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
