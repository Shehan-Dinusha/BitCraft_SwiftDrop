package com.swiftdrop.order.service.pricing;

import com.swiftdrop.order.config.PricingClientConfig;
import com.swiftdrop.order.service.PricingCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PricingClientWiringTest {

    @Configuration
    @Import({PricingCalculationService.class, LocalPricingFallback.class, PricingClientConfig.class})
    static class PricingBeans {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(PricingBeans.class)
            .withPropertyValues(
                    "pricing.service.url=http://localhost:8083",
                    "pricing.service.connect-timeout-ms=1000",
                    "pricing.service.read-timeout-ms=2000");

    @Test
    @DisplayName("mode=local selects the local fallback")
    void localModeSelectsFallback() {
        runner.withPropertyValues("pricing.mode=local").run(context -> {
            assertThat(context).hasSingleBean(PricingClient.class);
            assertThat(context.getBean(PricingClient.class)).isInstanceOf(LocalPricingFallback.class);
        });
    }

    @Test
    @DisplayName("A missing mode defaults to the local fallback")
    void missingModeSelectsFallback() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PricingClient.class);
            assertThat(context.getBean(PricingClient.class)).isInstanceOf(LocalPricingFallback.class);
        });
    }

    @Test
    @DisplayName("mode=remote selects the HTTP client and not the fallback")
    void remoteModeSelectsHttpClient() {
        runner.withPropertyValues("pricing.mode=remote").run(context -> {
            assertThat(context).hasSingleBean(PricingClient.class);
            assertThat(context.getBean(PricingClient.class)).isInstanceOf(HttpPricingClient.class);
            assertThat(context).doesNotHaveBean(LocalPricingFallback.class);
        });
    }
}
