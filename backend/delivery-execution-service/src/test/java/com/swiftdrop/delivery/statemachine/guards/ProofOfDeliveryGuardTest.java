package com.swiftdrop.delivery.statemachine.guards;

import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProofOfDeliveryGuardTest {

    @Mock
    private ProofOfDeliveryStorageService storageService;

    private ProofOfDeliveryGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProofOfDeliveryGuard(storageService);
    }

    @Test
    @DisplayName("isSatisfied returns true when proof of delivery is verified in storage")
    void testIsSatisfiedWhenVerified() {
        UUID deliveryId = UUID.randomUUID();
        when(storageService.isVerified(deliveryId)).thenReturn(true);

        boolean satisfied = guard.isSatisfied(deliveryId);

        assertThat(satisfied).isTrue();
        verify(storageService).isVerified(deliveryId);
    }

    @Test
    @DisplayName("isSatisfied returns false when proof of delivery is not yet verified")
    void testIsSatisfiedWhenNotVerified() {
        UUID deliveryId = UUID.randomUUID();
        when(storageService.isVerified(deliveryId)).thenReturn(false);

        boolean satisfied = guard.isSatisfied(deliveryId);

        assertThat(satisfied).isFalse();
        verify(storageService).isVerified(deliveryId);
    }

    @Test
    @DisplayName("isSatisfied returns false when deliveryId is null without querying storage")
    void testIsSatisfiedWithNullDeliveryId() {
        boolean satisfied = guard.isSatisfied(null);

        assertThat(satisfied).isFalse();
        verifyNoInteractions(storageService);
    }
}

