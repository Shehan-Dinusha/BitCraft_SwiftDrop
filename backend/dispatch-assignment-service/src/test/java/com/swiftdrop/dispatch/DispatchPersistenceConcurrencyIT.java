package com.swiftdrop.dispatch;

import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DispatchPersistenceConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Concurrent updates to one assignment allow only one versioned write")
    void optimisticLockingRejectsStaleAssignmentUpdate() throws Exception {
        UUID orderId = UUID.randomUUID();
        DispatchAssignment initial = new DispatchAssignment();
        initial.setOrderId(orderId);
        initial.setStatus(AssignmentStatus.UNASSIGNED);
        DispatchAssignment saved = assignmentRepository.saveAndFlush(initial);

        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        DispatchAssignment firstSnapshot = readTransaction.execute(status ->
                assignmentRepository.findById(saved.getId()).orElseThrow());
        DispatchAssignment secondSnapshot = readTransaction.execute(status ->
                assignmentRepository.findById(saved.getId()).orElseThrow());

        firstSnapshot.setStatus(AssignmentStatus.ASSIGNED);
        secondSnapshot.setStatus(AssignmentStatus.REASSIGNED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
            writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            Future<?> firstWrite = executor.submit(() ->
                    writeTransaction.executeWithoutResult(status ->
                            assignmentRepository.saveAndFlush(firstSnapshot)));
            Future<?> secondWrite = executor.submit(() ->
                    writeTransaction.executeWithoutResult(status ->
                            assignmentRepository.saveAndFlush(secondSnapshot)));

            int successfulWrites = 0;
            int optimisticLockFailures = 0;
            for (Future<?> write : new Future<?>[]{firstWrite, secondWrite}) {
                try {
                    write.get();
                    successfulWrites++;
                } catch (ExecutionException exception) {
                    if (hasOptimisticLockCause(exception)) {
                        optimisticLockFailures++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertThat(successfulWrites).isEqualTo(1);
            assertThat(optimisticLockFailures).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("The order uniqueness rule rejects a second dispatch assignment")
    void duplicateOrderAssignmentIsRejectedByDatabase() {
        UUID orderId = UUID.randomUUID();
        DispatchAssignment first = new DispatchAssignment();
        first.setOrderId(orderId);
        assignmentRepository.saveAndFlush(first);

        DispatchAssignment duplicate = new DispatchAssignment();
        duplicate.setOrderId(orderId);

        assertThatThrownBy(() -> assignmentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean hasOptimisticLockCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
