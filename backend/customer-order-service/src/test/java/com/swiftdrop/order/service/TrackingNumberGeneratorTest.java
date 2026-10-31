package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingNumberGeneratorTest {

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @InjectMocks
    private TrackingNumberGenerator generator;

    private static final Pattern TRACKING_PATTERN = Pattern.compile("^SD-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}$");

    @Test
    @DisplayName("Generated tracking number adheres strictly to SD-XXXX-XXXX non-sequential format")
    void testFormatCompliance() {
        when(customerOrderRepository.existsByTrackingNumber(anyString())).thenReturn(false);

        for (int i = 0; i < 50; i++) {
            String trackingNumber = generator.generateUniqueTrackingNumber();
            assertThat(trackingNumber).isNotNull();
            assertThat(TRACKING_PATTERN.matcher(trackingNumber).matches())
                    .as("Tracking number %s must match SD-XXXX-XXXX regex without ambiguous chars (0,1,O,I)", trackingNumber)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Generated tracking numbers are highly random and non-sequential")
    void testRandomnessAndUniqueness() {
        when(customerOrderRepository.existsByTrackingNumber(anyString())).thenReturn(false);

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String trackingNumber = generator.generateUniqueTrackingNumber();
            assertThat(generated.add(trackingNumber))
                    .as("Collision detected: %s was already generated", trackingNumber)
                    .isTrue();
        }
    }
}
