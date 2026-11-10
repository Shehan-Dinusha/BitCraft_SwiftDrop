package com.swiftdrop.order.service.pricing;

import com.swiftdrop.order.config.PricingClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PricingClientWiringTest {

    @Configuration
    @Import(PricingClientConfig.class)
    static class PricingBeans {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(PricingBeans.class)
            .withPropertyValues(
                    "pricing.service.url=http://localhost:8083",
                    "pricing.service.connect-timeout-ms=1000",
                    "pricing.service.read-timeout-ms=5000");

    @Test
    @DisplayName("The HTTP client is the only pricing implementation")
    void httpClientIsTheOnlyImplementation() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PricingClient.class);
            assertThat(context.getBean(PricingClient.class)).isInstanceOf(HttpPricingClient.class);
        });
    }
}
