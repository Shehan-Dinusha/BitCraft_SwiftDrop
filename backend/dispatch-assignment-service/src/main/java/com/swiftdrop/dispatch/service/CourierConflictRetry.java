package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.CourierShift;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * Re-runs an HTTP operation when it lost an optimistic-lock race on a courier's
 * shift record only, for example an assignment racing that courier's location
 * ping. Optimistic locking is unchanged: the losing transaction has already rolled
 * back and published nothing, and each attempt is a new transaction that re-reads
 * and re-validates everything (assignment state, capacity, availability).
 *
 * <p>Conflicts on the dispatch assignment itself are never retried: they mean
 * another dispatcher changed the order, and that 409 must reach the caller. The
 * operation must call a {@code @Transactional} service method and must not run
 * inside an outer transaction, otherwise retrying would reuse the failed one.</p>
 */
@Component
public class CourierConflictRetry {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(CourierConflictRetry.class);

    public <T> T run(String operation, Supplier<T> action) {
        boolean retryAllowed = !TransactionSynchronizationManager.isActualTransactionActive();
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (!retryAllowed || !isCourierConflict(ex) || attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.info("{} lost a courier record update race (attempt {} of {}); retrying",
                        operation, attempt, MAX_ATTEMPTS);
            }
        }
    }

    public static boolean isCourierConflict(ObjectOptimisticLockingFailureException ex) {
        return CourierShift.class.getName().equals(ex.getPersistentClassName());
    }
}
