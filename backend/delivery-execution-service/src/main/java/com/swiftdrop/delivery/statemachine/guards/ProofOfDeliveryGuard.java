package com.swiftdrop.delivery.statemachine.guards;

import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard for OUT_FOR_DELIVERY -> DELIVERED. A parcel only counts as delivered once the recipient
 * confirmed it (photo + recipient name, or signature) and that proof was stored; this protects customers
 * and merchants from "delivered" parcels that never arrived, and is the evidence for disputes.
 */
@Component
public class ProofOfDeliveryGuard {
    private final ProofOfDeliveryStorageService storageService;

    public ProofOfDeliveryGuard(ProofOfDeliveryStorageService storageService) {
        this.storageService = storageService;
    }

    /** @return true only when verified proof exists for the delivery; false for a null id */
    public boolean isSatisfied(UUID deliveryId) {
        if (deliveryId == null) {
            return false;
        }
        return storageService.isVerified(deliveryId);
    }
}
