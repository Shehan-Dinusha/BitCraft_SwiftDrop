package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.webhook.domain.repository.FailedDeliveryRepository;
import com.swiftdrop.webhook.dto.FailedDeliveryResponse;
import com.swiftdrop.webhook.exception.FailedDeliveryNotFoundException;
import com.swiftdrop.webhook.service.access.MerchantAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Failed deliveries and their replay. A merchant sees and replays only its own; a system admin any. Replays are
 * queued and sent by the delivery worker, so nothing here makes an HTTP call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedDeliveryService {

    private static final UserRole[] MANAGERS = {UserRole.MERCHANT, UserRole.SYSTEM_ADMIN};
    private static final int LIST_LIMIT = 200;

    private final FailedDeliveryRepository repository;

    @Transactional(readOnly = true)
    public List<FailedDeliveryResponse> list(CallerContext caller) {
        return repository.list(scope(caller), LIST_LIMIT);
    }

    /** Safe to repeat: one that is already waiting, running or delivered is returned as it is. */
    @Transactional
    public FailedDeliveryResponse replay(UUID deliveryId, CallerContext caller) {
        UUID merchantId = scope(caller);
        find(deliveryId, merchantId);
        int queued = repository.queueReplays(merchantId, deliveryId);
        log.info("Replay of failed webhook delivery {}: {}", deliveryId, queued == 1 ? "queued" : "nothing to do");
        return find(deliveryId, merchantId);
    }

    /** @return how many replays were queued */
    @Transactional
    public int replayAll(CallerContext caller) {
        int queued = repository.queueReplays(scope(caller), null);
        log.info("Replay of all failed webhook deliveries: {} queued", queued);
        return queued;
    }

    private FailedDeliveryResponse find(UUID deliveryId, UUID merchantId) {
        return repository.find(deliveryId, merchantId).orElseThrow(() ->
                new FailedDeliveryNotFoundException("Failed delivery not found: " + deliveryId));
    }

    private static UUID scope(CallerContext caller) {
        MerchantAccess.requireRole(caller, MANAGERS);
        return MerchantAccess.listScope(caller, null, UserRole.SYSTEM_ADMIN);
    }
}
