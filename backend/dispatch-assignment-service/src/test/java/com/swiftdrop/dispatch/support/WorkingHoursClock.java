package com.swiftdrop.dispatch.support;

import com.swiftdrop.dispatch.service.WorkingDay;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every Spring test context runs at Tuesday 24 November 2026, 10:00 Sri Lanka time: a working day, shifts open,
 * pickups still given out. The working-day rules would otherwise make tests depend on the day and hour they run.
 * Registered for all test contexts in {@code META-INF/spring.factories}.
 */
public class WorkingHoursClock implements ContextCustomizerFactory {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 10, 0);

    public static Clock clock() {
        return Clock.fixed(NOW.atZone(WorkingDay.ZONE).toInstant(), WorkingDay.ZONE);
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        return new FixedClock();
    }

    private static final class FixedClock implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
            if (context.getBeanFactory() instanceof BeanDefinitionRegistry registry) {
                RootBeanDefinition clock = new RootBeanDefinition(Clock.class, WorkingHoursClock::clock);
                clock.setPrimary(true);
                registry.registerBeanDefinition("workingHoursClock", clock);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FixedClock;
        }

        @Override
        public int hashCode() {
            return FixedClock.class.hashCode();
        }
    }
}
