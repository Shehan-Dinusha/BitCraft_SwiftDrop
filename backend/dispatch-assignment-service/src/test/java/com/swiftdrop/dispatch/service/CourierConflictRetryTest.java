package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourierConflictRetryTest {

    private final CourierConflictRetry retry = new CourierConflictRetry();
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("A courier-shift conflict is retried and the next successful attempt is returned")
    void courierConflictIsRetried() {
        String result = retry.run("Assignment", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw courierConflict();
            }
            return "assigned";
        });

        assertThat(result).isEqualTo("assigned");
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("Courier-shift conflicts stop after the bounded number of attempts")
    void courierConflictRetriesAreBounded() {
        assertThatThrownBy(() -> retry.run("Assignment", () -> {
            attempts.incrementAndGet();
            throw courierConflict();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(attempts).hasValue(CourierConflictRetry.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("An assignment conflict (another dispatcher) is never retried")
    void assignmentConflictIsNotRetried() {
        assertThatThrownBy(() -> retry.run("Assignment", () -> {
            attempts.incrementAndGet();
            throw new ObjectOptimisticLockingFailureException(DispatchAssignment.class, 1L);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("Other exceptions are not retried")
    void otherExceptionsAreNotRetried() {
        assertThatThrownBy(() -> retry.run("Assignment", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("unrelated");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("Inside an existing transaction nothing is retried, since it would reuse the failed transaction")
    void noRetryInsideAnActiveTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> retry.run("Assignment", () -> {
            attempts.incrementAndGet();
            throw courierConflict();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(attempts).hasValue(1);
    }

    private ObjectOptimisticLockingFailureException courierConflict() {
        return new ObjectOptimisticLockingFailureException(CourierShift.class, 7L);
    }
}
