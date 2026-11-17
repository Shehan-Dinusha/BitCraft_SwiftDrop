package com.swiftdrop.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.swiftdrop.dispatch.support.TestCallers.dispatcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Courier workload release through the real listeners, PostgreSQL and RabbitMQ:
 * terminal parcel.status.changed events (as delivery-execution publishes them) and
 * order.cancelled events are published to the exchange and consumed by dispatch.
 *
 * <p>Each listener has a single consumer and processes its queue in order, so a
 * marker event published after the events under test proves those were handled
 * before the final state is checked (used where the expected state equals an
 * intermediate one, such as a duplicate that must change nothing).</p>
 */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WorkloadReleaseIT {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"DELIVERED, COMPLETED", "FAILED, CANCELLED", "CANCELLED, CANCELLED"})
    @DisplayName("A terminal parcel.status.changed event closes the assignment and releases one workload slot")
    void terminalStatusReleasesWorkload(PublicOrderStatus terminal, AssignmentStatus expected) {
        UUID courierId = courier(2);
        UUID orderId = assignedOrder(courierId, AssignmentStatus.ASSIGNED);

        publishDeliveryStatus(orderId, terminal);

        await().atMost(WAIT).until(() -> assignmentStatus(orderId) == expected);
        assertThat(activeParcels(courierId)).as("3 active, one released").isEqualTo(2);
    }

    @Test
    @DisplayName("order.cancelled closes an assigned order and releases one workload slot")
    void orderCancelledReleasesWorkload() {
        UUID courierId = courier(2);
        UUID orderId = assignedOrder(courierId, AssignmentStatus.ASSIGNED);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CANCELLED,
                new OrderCancelledEvent(null, null, orderId, UUID.randomUUID(), "Customer request", "CUSTOMER", null));

        await().atMost(WAIT).until(() -> assignmentStatus(orderId) == AssignmentStatus.CANCELLED);
        assertThat(activeParcels(courierId)).as("3 active, one released").isEqualTo(2);
        assertThat(assignmentRepository.findByOrderId(orderId).orElseThrow().getFailureReason())
                .isEqualTo("Order cancelled: Customer request");
    }

    @Test
    @DisplayName("A duplicated terminal event releases the workload only once")
    void duplicateTerminalEventReleasesOnce() {
        UUID courierId = courier(2);
        UUID orderId = assignedOrder(courierId, AssignmentStatus.ASSIGNED);

        publishDeliveryStatus(orderId, PublicOrderStatus.DELIVERED);
        publishDeliveryStatus(orderId, PublicOrderStatus.DELIVERED);
        awaitStatusEventsProcessed();

        assertThat(assignmentStatus(orderId)).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(activeParcels(courierId)).as("3 active, one order delivered twice").isEqualTo(2);
    }

    @Test
    @DisplayName("A duplicated order.cancelled, and delivery's CANCELLED after it, release the workload only once")
    void duplicateCancellationReleasesOnce() {
        UUID courierId = courier(2);
        UUID orderId = assignedOrder(courierId, AssignmentStatus.ASSIGNED);
        OrderCancelledEvent cancelled =
                new OrderCancelledEvent(null, null, orderId, UUID.randomUUID(), "Customer request", "CUSTOMER", null);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CANCELLED, cancelled);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CANCELLED, cancelled);
        await().atMost(WAIT).until(() -> assignmentStatus(orderId) == AssignmentStatus.CANCELLED);
        awaitCancellationsProcessed();
        publishDeliveryStatus(orderId, PublicOrderStatus.CANCELLED);
        awaitStatusEventsProcessed();

        assertThat(activeParcels(courierId)).as("3 active, one order cancelled three times").isEqualTo(2);
    }

    @Test
    @DisplayName("A terminal event after a reassignment releases only the courier now holding the order")
    void terminalEventAfterReassignmentReleasesOnlyCurrentCourier() throws Exception {
        UUID previousCourier = courier(1); // keeps one other parcel after losing this order
        UUID currentCourier = courier(0);
        UUID orderId = assignedOrder(previousCourier, AssignmentStatus.ASSIGNED);
        mockMvc.perform(post("/api/v1/dispatch/reassign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReassignCourierRequest(
                                orderId, previousCourier, currentCourier, "IT_DISPATCHER", null))))
                .andExpect(status().isOk());
        assertThat(activeParcels(previousCourier)).isEqualTo(1);
        assertThat(activeParcels(currentCourier)).isEqualTo(1);

        publishDeliveryStatus(orderId, PublicOrderStatus.DELIVERED);

        await().atMost(WAIT).until(() -> assignmentStatus(orderId) == AssignmentStatus.COMPLETED);
        assertThat(activeParcels(currentCourier)).isZero();
        assertThat(activeParcels(previousCourier)).as("previous courier untouched").isEqualTo(1);
    }

    /** Same shape delivery-execution-service publishes: its own tracking number, orderId always set. */
    private void publishDeliveryStatus(UUID orderId, PublicOrderStatus status) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_PARCEL_STATUS,
                new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(),
                        "SD-" + orderId.toString().substring(0, 8).toUpperCase(), orderId, null,
                        PublicOrderStatus.OUT_FOR_DELIVERY, status, null,
                        SriLankanDistrict.COLOMBO, SriLankanDistrict.GAMPAHA, null, Instant.now()));
    }

    /** Waits until a marker status event published last has been applied. */
    private void awaitStatusEventsProcessed() {
        UUID marker = assignedOrder(courier(0), AssignmentStatus.ASSIGNED);
        publishDeliveryStatus(marker, PublicOrderStatus.DELIVERED);
        await().atMost(WAIT).until(() -> assignmentStatus(marker) == AssignmentStatus.COMPLETED);
    }

    /** Waits until a marker order.cancelled event published last has been applied. */
    private void awaitCancellationsProcessed() {
        UUID marker = unassignedOrder();
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CANCELLED,
                new OrderCancelledEvent(null, null, marker, UUID.randomUUID(), "Marker", "SYSTEM", null));
        await().atMost(WAIT).until(() -> assignmentStatus(marker) == AssignmentStatus.CANCELLED);
    }

    private AssignmentStatus assignmentStatus(UUID orderId) {
        return assignmentRepository.findByOrderId(orderId).orElseThrow().getStatus();
    }

    private int activeParcels(UUID courierId) {
        return courierShiftRepository.findByCourierId(courierId).orElseThrow().getActiveParcelsCount();
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

    /** An order held by the courier; the courier's workload is raised by one to match. */
    private UUID assignedOrder(UUID courierId, AssignmentStatus status) {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(UUID.randomUUID());
        assignment.setTrackingNumber("SD-IT-" + UUID.randomUUID().toString().substring(0, 8));
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setStatus(status);
        assignment.setCourierId(courierId);
        assignment.setCourierName("IT Courier");
        assignment.setAssignedAt(Instant.now());
        UUID orderId = assignmentRepository.saveAndFlush(assignment).getOrderId();

        CourierShift courier = courierShiftRepository.findByCourierId(courierId).orElseThrow();
        courier.setActiveParcelsCount(courier.getActiveParcelsCount() + 1);
        courierShiftRepository.saveAndFlush(courier);
        return orderId;
    }

    /** An AVAILABLE courier holding {@code otherParcels} parcels that no test event touches. */
    private UUID courier(int otherParcels) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(UUID.randomUUID());
        courier.setCourierName("IT Courier");
        courier.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        courier.setActiveParcelsCount(otherParcels);
        courier.setMaxCapacity(5);
        return courierShiftRepository.saveAndFlush(courier).getCourierId();
    }
}
