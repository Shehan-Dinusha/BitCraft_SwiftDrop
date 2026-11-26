package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.RecipientEmailStop;
import com.swiftdrop.notification.domain.repository.RecipientEmailStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "Stop these emails" for a parcel's recipient, who has no account and no settings. Each parcel gets one random
 * token (256 bits, the same shape as a staff invite token); the link in the email carries it, and the page it
 * opens asks before stopping, so a mail scanner that follows every link stops nothing.
 */
@Service
@RequiredArgsConstructor
public class RecipientEmailStopService {

    /** 32 random bytes, URL-safe base64 without padding. */
    public static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecipientEmailStopRepository repository;

    /** Whether the recipient of this parcel asked for no more emails. */
    public boolean isStopped(UUID orderId) {
        return orderId != null && repository.findById(orderId).map(RecipientEmailStop::isStopped).orElse(false);
    }

    /** The token for this parcel's stop link, made on its first recipient email. */
    public String tokenFor(UUID orderId) {
        Optional<RecipientEmailStop> existing = repository.findById(orderId);
        if (existing.isPresent()) {
            return existing.get().getToken();
        }
        try {
            return repository.saveAndFlush(RecipientEmailStop.builder().orderId(orderId).token(newToken()).build()).getToken();
        } catch (DataIntegrityViolationException raced) {
            // Two emails for the same parcel at once: the other one made the row first.
            return repository.findById(orderId).map(RecipientEmailStop::getToken).orElseThrow(() -> raced);
        }
    }

    /**
     * Stops the recipient emails of the parcel this token belongs to. Using the link again is fine.
     *
     * @return false when the token is unknown
     */
    public boolean stop(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return false;
        }
        return repository.findByToken(token).map(stop -> {
            if (!stop.isStopped()) {
                stop.setStoppedAt(Instant.now());
                repository.save(stop);
            }
            return true;
        }).orElse(false);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
