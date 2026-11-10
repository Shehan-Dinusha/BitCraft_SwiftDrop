package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.dto.MerchantApiKeyValidationResult;
import com.swiftdrop.merchant.domain.entity.MerchantApiKey;
import com.swiftdrop.merchant.domain.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.dto.ApiKeyResponse;
import com.swiftdrop.merchant.dto.CreateApiKeyRequest;
import com.swiftdrop.merchant.exception.ApiKeyNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantApiKeyService {

    private final MerchantApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a new merchant API key.
     * The raw key is returned ONLY ONCE in the response; only its SHA-256 hash is persisted.
     */
    @Transactional
    public ApiKeyResponse generateApiKey(UUID merchantId, CreateApiKeyRequest request) {
        log.info("Generating new API key for merchant: {}, name: {}", merchantId, request.keyName());

        Instant expiresAt = null;
        if (request.expiresInDays() != null && request.expiresInDays() > 0) {
            expiresAt = Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS);
        }
        return issueApiKey(merchantId, request.keyName(), expiresAt);
    }

    /** Serializes rotations of the same key; both writes commit or roll back together. */
    @Transactional
    public ApiKeyResponse regenerateApiKey(UUID merchantId, UUID keyId) {
        MerchantApiKey oldKey = apiKeyRepository.findForUpdate(keyId)
                .filter(key -> key.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new ApiKeyNotFoundException("API key not found: " + keyId));
        Instant now = Instant.now();
        if (!oldKey.isActive() || oldKey.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A revoked API key cannot be regenerated");
        }
        if (oldKey.getExpiresAt() != null && !oldKey.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An expired API key cannot be regenerated");
        }

        ApiKeyResponse replacement = issueApiKey(merchantId, oldKey.getKeyName(), oldKey.getExpiresAt());
        oldKey.setActive(false);
        oldKey.setRevokedAt(now);
        apiKeyRepository.save(oldKey);
        return replacement;
    }

    private ApiKeyResponse issueApiKey(UUID merchantId, String keyName, Instant expiresAt) {

        // Generate 32 cryptographically secure random bytes
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String rawApiKey = "sk_live_" + token;

        String keyPrefix = "sk_live_" + token.substring(0, Math.min(6, token.length())) + "...";
        String keyHash = sha256(rawApiKey);

        MerchantApiKey apiKey = MerchantApiKey.builder()
                .merchantId(merchantId)
                .keyName(keyName)
                .keyPrefix(keyPrefix)
                .keyHash(keyHash)
                .active(true)
                .expiresAt(expiresAt)
                .build();

        MerchantApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyResponse(
                saved.getKeyId(),
                saved.getMerchantId(),
                saved.getKeyName(),
                saved.getKeyPrefix(),
                saved.isActive(),
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                saved.getLastUsedAt(),
                saved.getRevokedAt(),
                rawApiKey // ONLY populated upon generation
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID merchantId) {
        return apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(this::mapToResponseWithoutSecret)
                .toList();
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(UUID merchantId, UUID keyId) {
        log.info("Revoking API key: {} for merchant: {}", keyId, merchantId);
        MerchantApiKey apiKey = apiKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new ApiKeyNotFoundException("API key not found: " + keyId));

        if (!apiKey.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("API key does not belong to merchant: " + merchantId);
        }

        apiKey.setActive(false);
        apiKey.setRevokedAt(Instant.now());
        MerchantApiKey updated = apiKeyRepository.save(apiKey);

        return mapToResponseWithoutSecret(updated);
    }

    /**
     * Validates an API key (invoked by API Gateway filter via /internal/v1/api-keys/validate).
     */
    @Transactional
    public MerchantApiKeyValidationResult validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return MerchantApiKeyValidationResult.invalid("Missing API key");
        }

        String keyHash = sha256(rawApiKey.trim());
        return apiKeyRepository.findByKeyHash(keyHash)
                .map(key -> {
                    if (!key.isActive()) {
                        return MerchantApiKeyValidationResult.invalid("API key has been revoked");
                    }
                    if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(Instant.now())) {
                        return MerchantApiKeyValidationResult.invalid("API key has expired");
                    }
                    if (key.getKeyHash() == null || !MessageDigest.isEqual(
                            keyHash.getBytes(StandardCharsets.US_ASCII),
                            key.getKeyHash().getBytes(StandardCharsets.US_ASCII))) {
                        return MerchantApiKeyValidationResult.invalid("API key not recognized");
                    }
                    // Update last used timestamp
                    key.setLastUsedAt(Instant.now());
                    apiKeyRepository.save(key);

                    return MerchantApiKeyValidationResult.valid(key.getMerchantId());
                })
                .orElseGet(() -> MerchantApiKeyValidationResult.invalid("API key not recognized"));
    }

    private ApiKeyResponse mapToResponseWithoutSecret(MerchantApiKey key) {
        return new ApiKeyResponse(
                key.getKeyId(),
                key.getMerchantId(),
                key.getKeyName(),
                key.getKeyPrefix(),
                key.isActive(),
                key.getCreatedAt(),
                key.getExpiresAt(),
                key.getLastUsedAt(),
                key.getRevokedAt(),
                null // Raw key is never exposed after creation
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
