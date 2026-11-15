package com.swiftdrop.dispatch;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves dispatch events reach RabbitMQ only after the assignment transaction
 * commits. {@link RabbitTemplate} is mocked so each test can observe exactly when,
 * and whether, a send happens; listener containers are not started.
 */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class AfterCommitEventPublishingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ManualOverrideService manualOverrideService;

    @Autowired
    private DispatchEventPublisher dispatchEventPublisher;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void ignoreFrameworkStartupCalls() {
        // Spring's RabbitMessagingTemplate reads the converter from this mock while
        // the context starts; only calls made by the code under test matter here.
        clearInvocations(rabbitTemplate);
    }

    @Test
    @DisplayName("Committed manual assignment publishes its events only after commit")
    void committedAssignmentPublishesAfterCommit() {
        UUID orderId = unassignedOrder();
        UUID courierId = availableCourier();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            manualOverrideService.manualAssign(request(orderId, courierId));
            verifyNoInteractions(rabbitTemplate);
        });

        InOrder broker = inOrder(rabbitTemplate);
        broker.verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED), isA(CourierAssignedEvent.class));
        broker.verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_PARCEL_STATUS), isA(ParcelStatusChangedEvent.class));
        broker.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Rolled-back manual assignment publishes no events and changes nothing")
    void rolledBackAssignmentPublishesNothing() {
        UUID orderId = unassignedOrder();
        UUID courierId = availableCourier();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            manualOverrideService.manualAssign(request(orderId, courierId));
            status.setRollbackOnly();
        });

        verifyNoInteractions(rabbitTemplate);
        assertThat(assignmentRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(courierShiftRepository.findByCourierId(courierId).orElseThrow().getActiveParcelsCount())
                .isZero();
    }

    @Test
    @DisplayName("Assignment that loses an optimistic-lock race at commit publishes no events")
    void assignmentFailingAtCommitPublishesNothing() {
        UUID orderId = unassignedOrder();
        UUID courierId = availableCourier();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrentWriter = new TransactionTemplate(transactionManager);
        concurrentWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            manualOverrideService.manualAssign(request(orderId, courierId));
            // Another operation commits a change to the same row first, so this
            // transaction's versioned UPDATE fails when it flushes at commit.
            concurrentWriter.executeWithoutResult(inner -> {
                DispatchAssignment competing = assignmentRepository.findByOrderId(orderId).orElseThrow();
                competing.setFailureReason("changed by a concurrent operation");
                assignmentRepository.save(competing);
            });
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verifyNoInteractions(rabbitTemplate);
        assertThat(assignmentRepository.findByOrderId(orderId).orElseThrow().getCourierId()).isNull();
    }

    @Test
    @DisplayName("Publishing outside a transaction still sends immediately")
    void nonTransactionalPublishSendsImmediately() {
        CourierAssignedEvent event = new CourierAssignedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Test Courier", 20, Instant.now(), Instant.now());

        dispatchEventPublisher.publishCourierAssigned(event);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED, (Object) event);
    }

    private UUID unassignedOrder() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(UUID.randomUUID());
        assignment.setTrackingNumber("SD-IT-" + UUID.randomUUID().toString().substring(0, 8));
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setStatus(AssignmentStatus.UNASSIGNED);
        return assignmentRepository.saveAndFlush(assignment).getOrderId();
    }

    @Test
    @DisplayName("Committed reassignment publishes only courier.assigned after commit, with the new courier and correlation ID")
    void committedReassignmentPublishesCorrectEventsAfterCommit() {
        UUID oldCourier = availableCourier();
        UUID newCourier = availableCourier();
        UUID orderId = assignedOrder(oldCourier);
        UUID correlationId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            manualOverrideService.reassign(reassignRequest(orderId, oldCourier, newCourier), correlationId);
            verifyNoInteractions(rabbitTemplate);
        });

        ArgumentCaptor<CourierAssignedEvent> assigned = ArgumentCaptor.forClass(CourierAssignedEvent.class);
        InOrder broker = inOrder(rabbitTemplate);
        broker.verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED), assigned.capture());
        // No parcel.status.changed: the public status stays PICKUP_ASSIGNED on reassignment.
        broker.verifyNoMoreInteractions();

        assertThat(assigned.getValue().courierId()).isEqualTo(newCourier);
        assertThat(assigned.getValue().correlationId()).isEqualTo(correlationId);
        assertThat(workload(oldCourier)).isZero();
        assertThat(workload(newCourier)).isEqualTo(1);
    }

    @Test
    @DisplayName("Rolled-back reassignment publishes nothing and leaves the assignment and workloads unchanged")
    void rolledBackReassignmentPublishesNothing() {
        UUID oldCourier = availableCourier();
        UUID newCourier = availableCourier();
        UUID orderId = assignedOrder(oldCourier);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            manualOverrideService.reassign(reassignRequest(orderId, oldCourier, newCourier), UUID.randomUUID());
            status.setRollbackOnly();
        });

        verifyNoInteractions(rabbitTemplate);
        DispatchAssignment unchanged = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(unchanged.getCourierId()).isEqualTo(oldCourier);
        assertThat(unchanged.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(workload(oldCourier)).isEqualTo(1);
        assertThat(workload(newCourier)).isZero();
    }

    @Test
    @DisplayName("Rejected reassignment (stale expected courier) publishes nothing")
    void rejectedReassignmentPublishesNothing() {
        UUID oldCourier = availableCourier();
        UUID newCourier = availableCourier();
        UUID orderId = assignedOrder(oldCourier);

        assertThatThrownBy(() -> manualOverrideService.reassign(
                reassignRequest(orderId, UUID.randomUUID(), newCourier), UUID.randomUUID()))
                .isInstanceOf(AssignmentConflictException.class);

        verifyNoInteractions(rabbitTemplate);
        assertThat(assignmentRepository.findByOrderId(orderId).orElseThrow().getCourierId()).isEqualTo(oldCourier);
        assertThat(workload(oldCourier)).isEqualTo(1);
        assertThat(workload(newCourier)).isZero();
    }

    private UUID assignedOrder(UUID courierId) {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(UUID.randomUUID());
        assignment.setTrackingNumber("SD-IT-" + UUID.randomUUID().toString().substring(0, 8));
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setCourierId(courierId);
        assignment.setCourierName("IT Courier");
        assignment.setAssignedAt(Instant.now());
        UUID orderId = assignmentRepository.saveAndFlush(assignment).getOrderId();

        CourierShift courier = courierShiftRepository.findByCourierId(courierId).orElseThrow();
        courier.setActiveParcelsCount(1);
        courierShiftRepository.saveAndFlush(courier);
        return orderId;
    }

    private int workload(UUID courierId) {
        return courierShiftRepository.findByCourierId(courierId).orElseThrow().getActiveParcelsCount();
    }

    private ReassignCourierRequest reassignRequest(UUID orderId, UUID expectedCourier, UUID newCourier) {
        return new ReassignCourierRequest(orderId, expectedCourier, newCourier, "IT_SUPERVISOR", null);
    }

    private UUID availableCourier() {
        CourierShift courier = new CourierShift();
        courier.setCourierId(UUID.randomUUID());
        courier.setCourierName("IT Courier");
        courier.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        courier.setActiveParcelsCount(0);
        courier.setMaxCapacity(5);
        return courierShiftRepository.saveAndFlush(courier).getCourierId();
    }

    private ManualAssignRequest request(UUID orderId, UUID courierId) {
        return new ManualAssignRequest(orderId, courierId, "IT_DISPATCHER", null);
    }
}
