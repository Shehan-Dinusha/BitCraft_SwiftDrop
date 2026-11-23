package com.swiftdrop.dispatch.support;

import com.swiftdrop.dispatch.service.WorkingDay;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Fixes the clock at Tuesday 24 November 2026, 10:00 Sri Lanka time, so tests that start shifts do not depend
 * on the day or hour they run.
 */
@TestConfiguration
public class WorkingHoursClock {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Bean
    @Primary
    public Clock workingHoursClock() {
        return Clock.fixed(NOW.atZone(WorkingDay.ZONE).toInstant(), WorkingDay.ZONE);
    }
}
