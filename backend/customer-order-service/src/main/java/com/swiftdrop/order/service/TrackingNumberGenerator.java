package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates secure, non-sequential, Crockford-inspired tracking numbers
 * formatted as {@code SD-XXXX-XXXX} (e.g., {@code SD-7K4M-9QX2}).
 * Excludes ambiguous characters (0, 1, I, O) to prevent human reading errors.
 */
@Component
@RequiredArgsConstructor
public class TrackingNumberGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerOrderRepository customerOrderRepository;

    public String generateUniqueTrackingNumber() {
        String trackingNumber;
        int attempts = 0;
        do {
            trackingNumber = "SD-" + generateSegment(4) + "-" + generateSegment(4);
            attempts++;
            if (attempts > 50) {
                throw new IllegalStateException("Unable to generate unique tracking number after multiple attempts");
            }
        } while (customerOrderRepository.existsByTrackingNumber(trackingNumber));

        return trackingNumber;
    }

    private String generateSegment(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
