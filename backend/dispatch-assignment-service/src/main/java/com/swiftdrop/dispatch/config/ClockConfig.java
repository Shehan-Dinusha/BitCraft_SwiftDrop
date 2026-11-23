package com.swiftdrop.dispatch.config;

import com.swiftdrop.dispatch.service.WorkingDay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** The clock the working-day rules read, in Sri Lanka time; tests replace it with a fixed one. */
@Configuration
@EnableScheduling
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(WorkingDay.ZONE);
    }
}
