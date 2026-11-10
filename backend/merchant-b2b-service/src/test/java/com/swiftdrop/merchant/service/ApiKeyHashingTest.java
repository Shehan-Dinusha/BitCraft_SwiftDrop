package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.entity.MerchantApiKey;
import com.swiftdrop.merchant.domain.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.dto.ApiKeyResponse;
import com.swiftdrop.merchant.dto.CreateApiKeyRequest;
import com.swiftdrop.merchant.exception.ApiKeyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyHashingTest {
    private final MerchantApiKeyRepository repository = mock(MerchantApiKeyRepository.class);
    private final Map<String, MerchantApiKey> stored = new HashMap<>();
    private final UUID merchantId = UUID.randomUUID();
    private MerchantApiKeyService service;

    @BeforeEach
    void setup() {
        service = new MerchantApiKeyService(repository);
        when(repository.save(any())).thenAnswer(invocation -> {
            MerchantApiKey key = invocation.getArgument(0);
            key.prePersist();
            stored.put(key.getKeyHash(), key);
            return key;
        });
        when(repository.findByKeyHash(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.findForUpdate(any())).thenAnswer(invocation -> stored.values().stream()
                .filter(key -> key.getKeyId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findByKeyId(any())).thenAnswer(invocation -> stored.values().stream()
                .filter(key -> key.getKeyId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findByMerchantIdOrderByCreatedAtDesc(any())).thenAnswer(invocation -> stored.values().stream()
                .filter(key -> key.getMerchantId().equals(invocation.getArgument(0))).toList());
    }

    @Test
    void storesSha256InsteadOfRawSecretAndAcceptsOnlyCorrectKey() throws Exception {
        ApiKeyResponse key = generate();
        MerchantApiKey saved = stored.values().iterator().next();
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(key.rawApiKey().getBytes(StandardCharsets.UTF_8)));
        assertThat(saved.getKeyHash()).isEqualTo(expectedHash).isNotEqualTo(key.rawApiKey());
        assertThat(saved.getLastUsedAt()).isNull();
        assertThat(service.validateApiKey(key.rawApiKey()).valid()).isTrue();
        Instant lastUsed = saved.getLastUsedAt();
        assertThat(lastUsed).isNotNull();
        char replacement = key.rawApiKey().charAt(key.rawApiKey().length() - 1) == 'A' ? 'B' : 'A';
        String changed = key.rawApiKey().substring(0, key.rawApiKey().length() - 1) + replacement;
        assertThat(service.validateApiKey(changed).valid()).isFalse();
        assertThat(saved.getLastUsedAt()).isEqualTo(lastUsed);
        assertThat(service.listApiKeys(merchantId)).allSatisfy(result -> assertThat(result.rawApiKey()).isNull());
    }

    @Test
    void revokedAndExpiredKeysDoNotUpdateLastUsed() {
        ApiKeyResponse revoked = generate();
        service.revokeApiKey(merchantId, revoked.keyId());
        assertThat(service.validateApiKey(revoked.rawApiKey()).valid()).isFalse();
        assertThat(find(revoked.keyId()).getLastUsedAt()).isNull();

        ApiKeyResponse expired = generate();
        find(expired.keyId()).setExpiresAt(Instant.now().minusSeconds(1));
        assertThat(service.validateApiKey(expired.rawApiKey()).valid()).isFalse();
        assertThat(find(expired.keyId()).getLastUsedAt()).isNull();
    }

    @Test
    void regenerationPreservesLabelAndExpiryAndInvalidatesOldSecret() {
        ApiKeyResponse old = generate();
        ApiKeyResponse replacement = service.regenerateApiKey(merchantId, old.keyId());
        assertThat(replacement.keyId()).isNotEqualTo(old.keyId());
        assertThat(replacement.rawApiKey()).isNotEqualTo(old.rawApiKey());
        assertThat(replacement.keyName()).isEqualTo(old.keyName());
        assertThat(replacement.expiresAt()).isEqualTo(old.expiresAt());
        assertThat(replacement.lastUsedAt()).isNull();
        assertThat(replacement.revokedAt()).isNull();
        assertThat(find(old.keyId()).isActive()).isFalse();
        assertThat(find(old.keyId()).getRevokedAt()).isNotNull();
        assertThat(service.validateApiKey(old.rawApiKey()).valid()).isFalse();
        assertThat(service.validateApiKey(replacement.rawApiKey()).valid()).isTrue();
        assertThat(service.listApiKeys(merchantId)).allSatisfy(key -> assertThat(key.rawApiKey()).isNull());
    }

    @Test
    void regenerationPreservesNonExpiringKeys() {
        ApiKeyResponse old = service.generateApiKey(merchantId, new CreateApiKeyRequest("ERP", null));
        assertThat(service.regenerateApiKey(merchantId, old.keyId()).expiresAt()).isNull();
    }

    @Test
    void foreignOrUnknownKeyCannotBeRegenerated() {
        ApiKeyResponse old = generate();
        assertThatThrownBy(() -> service.regenerateApiKey(UUID.randomUUID(), old.keyId()))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> service.regenerateApiKey(merchantId, UUID.randomUUID()))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(stored).hasSize(1);
        assertThat(find(old.keyId()).isActive()).isTrue();
    }

    @Test
    void repeatedRotationAndExpiredKeysAreConflicts() {
        ApiKeyResponse old = generate();
        service.regenerateApiKey(merchantId, old.keyId());
        assertThatThrownBy(() -> service.regenerateApiKey(merchantId, old.keyId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        ApiKeyResponse expired = generate();
        find(expired.keyId()).setExpiresAt(Instant.now().minusSeconds(1));
        int count = stored.size();
        assertThatThrownBy(() -> service.regenerateApiKey(merchantId, expired.keyId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(stored).hasSize(count);
    }

    @Test
    void failedReplacementCreationLeavesOldKeyUsable() {
        ApiKeyResponse old = generate();
        doThrow(new IllegalStateException("Database unavailable")).when(repository).save(any());
        assertThatThrownBy(() -> service.regenerateApiKey(merchantId, old.keyId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(find(old.keyId()).isActive()).isTrue();
        assertThat(find(old.keyId()).getRevokedAt()).isNull();
    }

    @Test
    void rejectsRepositoryHashMismatchWithoutRecordingUse() {
        ApiKeyResponse key = generate();
        MerchantApiKey saved = find(key.keyId());
        saved.setKeyHash("0".repeat(64));
        assertThat(service.validateApiKey(key.rawApiKey()).valid()).isFalse();
        assertThat(saved.getLastUsedAt()).isNull();
    }

    private ApiKeyResponse generate() {
        return service.generateApiKey(merchantId, new CreateApiKeyRequest("ERP integration", 30));
    }

    private MerchantApiKey find(UUID id) {
        return stored.values().stream().filter(key -> key.getKeyId().equals(id)).findFirst().orElseThrow();
    }
}
