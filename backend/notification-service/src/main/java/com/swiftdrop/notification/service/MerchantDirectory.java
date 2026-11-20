package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.MerchantAccount;
import com.swiftdrop.notification.domain.repository.MerchantAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Knows which ids are merchant companies. For a merchant order the order's customerId is the merchant id,
 * so parcel updates for it name a company, not a person: they go to the merchant team inbox and are never
 * emailed or put in a personal inbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantDirectory {

    private final MerchantAccountRepository repository;

    /** Records a merchant company (idempotent). */
    public void remember(UUID merchantId) {
        if (merchantId == null || repository.existsById(merchantId)) {
            return;
        }
        try {
            repository.save(new MerchantAccount(merchantId, Instant.now()));
        } catch (DataAccessException e) {
            // Two events for a new merchant at once: the other one saved it.
            log.debug("Merchant {} already recorded: {}", merchantId, e.getMessage());
        }
    }

    public boolean isMerchant(UUID id) {
        return id != null && repository.existsById(id);
    }
}
