package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.RecipientPreferenceResponse;
import com.swiftdrop.notification.dto.UpdatePreferenceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipientPreferenceService {

    private final RecipientPreferenceRepository preferenceRepository;

    @Transactional
    public RecipientPreferenceResponse getOrCreatePreferences(UUID recipientId) {
        RecipientPreference pref = preferenceRepository.findByRecipientId(recipientId)
                .orElseGet(() -> {
                    log.info("Auto-provisioning default notification preferences for recipient: {}", recipientId);
                    RecipientPreference newPref = RecipientPreference.builder()
                            .recipientId(recipientId)
                            .recipientType("CUSTOMER")
                            .emailEnabled(true)
                            .smsEnabled(true)
                            .pushEnabled(true)
                            .languagePreference("en")
                            .build();
                    return preferenceRepository.save(newPref);
                });

        return mapToResponse(pref);
    }

    @Transactional
    public RecipientPreferenceResponse updatePreferences(UUID recipientId, UpdatePreferenceRequest request) {
        log.info("Updating notification preferences for recipient: {}", recipientId);

        RecipientPreference pref = preferenceRepository.findByRecipientId(recipientId)
                .orElseGet(() -> RecipientPreference.builder()
                        .recipientId(recipientId)
                        .recipientType("CUSTOMER")
                        .build());

        if (request.emailEnabled() != null) {
            pref.setEmailEnabled(request.emailEnabled());
        }
        if (request.smsEnabled() != null) {
            pref.setSmsEnabled(request.smsEnabled());
        }
        if (request.pushEnabled() != null) {
            pref.setPushEnabled(request.pushEnabled());
        }
        if (request.emailAddress() != null && !request.emailAddress().isBlank()) {
            pref.setEmailAddress(request.emailAddress().trim());
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            pref.setPhoneNumber(request.phoneNumber().trim());
        }
        if (request.languagePreference() != null && !request.languagePreference().isBlank()) {
            pref.setLanguagePreference(request.languagePreference().trim());
        }

        RecipientPreference saved = preferenceRepository.save(pref);
        return mapToResponse(saved);
    }

    private RecipientPreferenceResponse mapToResponse(RecipientPreference pref) {
        return new RecipientPreferenceResponse(
                pref.getRecipientId(),
                pref.getRecipientType(),
                pref.isEmailEnabled(),
                pref.isSmsEnabled(),
                pref.isPushEnabled(),
                pref.getEmailAddress(),
                pref.getPhoneNumber(),
                pref.getLanguagePreference(),
                pref.getUpdatedAt()
        );
    }
}
