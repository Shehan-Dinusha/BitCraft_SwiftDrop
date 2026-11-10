package com.swiftdrop.analytics.config;

import com.swiftdrop.analytics.service.AnalyticsProjectionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class AnalyticsProjectionStartup implements ApplicationRunner {
    private final AnalyticsProjectionService projections;

    public AnalyticsProjectionStartup(AnalyticsProjectionService projections) { this.projections = projections; }

    @Bean
    public Clock analyticsClock() { return Clock.systemUTC(); }

    @Override
    public void run(ApplicationArguments args) {
        // Only accepted inbox records are replayed; legacy sample rows are not facts.
        while (projections.catchUpBatch() > 0) { /* bounded transactions */ }
    }
}
