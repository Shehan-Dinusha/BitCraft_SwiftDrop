package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.RecipientPreferenceResponse;
import com.swiftdrop.notification.dto.UpdatePreferenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipientPreferenceServiceTest {

    @Mock
    private RecipientPreferenceRepository preferenceRepository;

    @InjectMocks
    private RecipientPreferenceService preferenceService;

    private UUID recipientId;

    @BeforeEach
    void setUp() {
        recipientId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should auto-provision default preferences if record does not exist")
    void testGetOrCreatePreferences_AutoProvision() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());

        RecipientPreference saved = RecipientPreference.builder()
                .recipientId(recipientId)
                .recipientType("CUSTOMER")
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(true)
                .languagePreference("en")
                .updatedAt(Instant.now())
                .build();

        when(preferenceRepository.save(any(RecipientPreference.class))).thenReturn(saved);

        RecipientPreferenceResponse response = preferenceService.getOrCreatePreferences(recipientId);

        assertThat(response.recipientId()).isEqualTo(recipientId);
        assertThat(response.emailEnabled()).isTrue();
        assertThat(response.smsEnabled()).isTrue();
        assertThat(response.pushEnabled()).isTrue();
        assertThat(response.languagePreference()).isEqualTo("en");

        verify(preferenceRepository).save(any(RecipientPreference.class));
    }

    @Test
    @DisplayName("Should return existing preferences if found")
    void testGetOrCreatePreferences_Existing() {
        RecipientPreference existing = RecipientPreference.builder()
                .recipientId(recipientId)
                .recipientType("CUSTOMER")
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(true)
                .emailAddress("user@example.com")
                .phoneNumber("+94771234567")
                .languagePreference("si")
                .updatedAt(Instant.now())
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(existing));

        RecipientPreferenceResponse response = preferenceService.getOrCreatePreferences(recipientId);

        assertThat(response.recipientId()).isEqualTo(recipientId);
        assertThat(response.smsEnabled()).isFalse();
        assertThat(response.emailAddress()).isEqualTo("user@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+94771234567");
        assertThat(response.languagePreference()).isEqualTo("si");

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update existing preferences")
    void testUpdatePreferences_Existing() {
        RecipientPreference existing = RecipientPreference.builder()
                .recipientId(recipientId)
                .recipientType("CUSTOMER")
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(true)
                .languagePreference("en")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(RecipientPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePreferenceRequest request = new UpdatePreferenceRequest(
                false,
                true,
                false,
                "newmail@example.com",
                "+94770000000",
                "ta"
        );

        RecipientPreferenceResponse response = preferenceService.updatePreferences(recipientId, request);

        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.smsEnabled()).isTrue();
        assertThat(response.pushEnabled()).isFalse();
        assertThat(response.emailAddress()).isEqualTo("newmail@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+94770000000");
        assertThat(response.languagePreference()).isEqualTo("ta");
    }

    @Test
    @DisplayName("Should create and update preferences if recipient not previously found")
    void testUpdatePreferences_NewRecord() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(RecipientPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePreferenceRequest request = new UpdatePreferenceRequest(
                true,
                false,
                true,
                "created@example.com",
                "+94711112222",
                "en"
        );

        RecipientPreferenceResponse response = preferenceService.updatePreferences(recipientId, request);

        assertThat(response.recipientId()).isEqualTo(recipientId);
        assertThat(response.emailAddress()).isEqualTo("created@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+94711112222");
    }
}
