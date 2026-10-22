package com.swiftdrop.delivery.statemachine.guards;

import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProofOfDeliveryGuard {
    private final ProofOfDeliveryStorageService storageService;

    public ProofOfDeliveryGuard(ProofOfDeliveryStorageService storageService) {
        this.storageService = storageService;
    }

    public boolean isSatisfied(UUID deliveryId) {
        return storageService.isVerified(deliveryId);
    }
}
