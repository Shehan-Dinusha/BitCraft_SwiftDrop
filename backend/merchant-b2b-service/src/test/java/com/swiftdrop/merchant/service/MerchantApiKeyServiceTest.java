package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.dto.MerchantApiKeyValidationResult;
import com.swiftdrop.merchant.domain.entity.MerchantApiKey;
import com.swiftdrop.merchant.domain.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.dto.ApiKeyResponse;
import com.swiftdrop.merchant.dto.CreateApiKeyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyServiceTest {

    @Mock
    private MerchantApiKeyRepository apiKeyRepository;

    private MerchantApiKeyService apiKeyService;

    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        apiKeyService = new MerchantApiKeyService(apiKeyRepository);
    }

    @Test
    @DisplayName("generateApiKey should produce sk_live_ token and store SHA-256 hash")
    void testGenerateApiKey() {
        CreateApiKeyRequest request = new CreateApiKeyRequest("ERP System Key", 30);

        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenAnswer(invocation -> {
            MerchantApiKey key = invocation.getArgument(0);
            key.setKeyId(UUID.randomUUID());
            key.setCreatedAt(Instant.now());
            return key;
        });

        ApiKeyResponse response = apiKeyService.generateApiKey(merchantId, request);

        assertNotNull(response);
        assertNotNull(response.rawApiKey());
        assertTrue(response.rawApiKey().startsWith("sk_live_"));
        assertTrue(response.keyPrefix().startsWith("sk_live_"));
        assertEquals("ERP System Key", response.keyName());
        assertTrue(response.active());
        assertNotNull(response.expiresAt());

        verify(apiKeyRepository).save(any(MerchantApiKey.class));
    }

    @Test
    @DisplayName("validateApiKey should return valid when key hash exists and is active")
    void testValidateApiKeySuccess() {
        String rawKey = "sk_live_test_raw_token_value_here";

        MerchantApiKey storedKey = MerchantApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyName("Active Key")
                .keyPrefix("sk_live_test...")
                .keyHash("hashed_value")
                .active(true)
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();

        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(storedKey));
        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenReturn(storedKey);

        MerchantApiKeyValidationResult result = apiKeyService.validateApiKey(rawKey);

        assertTrue(result.valid());
        assertEquals(merchantId, result.merchantId());
        assertNull(result.reason());
        assertNotNull(storedKey.getLastUsedAt());
    }

    @Test
    @DisplayName("validateApiKey should return invalid when key is revoked")
    void testValidateApiKeyRevoked() {
        MerchantApiKey revokedKey = MerchantApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyName("Revoked Key")
                .active(false)
                .revokedAt(Instant.now())
                .build();

        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(revokedKey));

        MerchantApiKeyValidationResult result = apiKeyService.validateApiKey("sk_live_revoked_key");

        assertFalse(result.valid());
        assertEquals("API key has been revoked", result.reason());
    }

    @Test
    @DisplayName("validateApiKey should return invalid when key is expired")
    void testValidateApiKeyExpired() {
        MerchantApiKey expiredKey = MerchantApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyName("Expired Key")
                .active(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(expiredKey));

        MerchantApiKeyValidationResult result = apiKeyService.validateApiKey("sk_live_expired_key");

        assertFalse(result.valid());
        assertEquals("API key has expired", result.reason());
    }

    @Test
    @DisplayName("revokeApiKey should set active to false and record revokedAt")
    void testRevokeApiKey() {
        UUID keyId = UUID.randomUUID();
        MerchantApiKey activeKey = MerchantApiKey.builder()
                .keyId(keyId)
                .merchantId(merchantId)
                .keyName("Key to Revoke")
                .active(true)
                .build();

        when(apiKeyRepository.findByKeyId(keyId)).thenReturn(Optional.of(activeKey));
        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyResponse response = apiKeyService.revokeApiKey(merchantId, keyId);

        assertFalse(response.active());
        assertNotNull(response.revokedAt());
        assertNull(response.rawApiKey());
    }

    @Test
    @DisplayName("listApiKeys should return list without raw secrets")
    void testListApiKeys() {
        MerchantApiKey key = MerchantApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyName("Key 1")
                .keyPrefix("sk_live_123...")
                .active(true)
                .build();

        when(apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)).thenReturn(List.of(key));

        List<ApiKeyResponse> list = apiKeyService.listApiKeys(merchantId);

        assertEquals(1, list.size());
        assertNull(list.get(0).rawApiKey());
    }
}
