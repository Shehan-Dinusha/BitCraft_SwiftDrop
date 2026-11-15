package com.swiftdrop.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pickup-aware reassignment against PostgreSQL and RabbitMQ: delivery-execution
 * style parcel.status.changed events are consumed by the real listener, and
 * /reassign is called over HTTP.
 *
 * <p>Known limitation (not testable here): dispatch learns about pickup only when
 * the status event is processed. A reassignment made between the physical pickup
 * and that moment succeeds; closing that window needs delivery-execution-service.</p>
 */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PickupAwareReassignmentIT {

    private static final String COURIER_ASSIGNED_PROBE = "it.pickup.courier-assigned";
    private static final String STATUS_PROBE = "it.pickup.parcel-status";

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
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @BeforeEach
    void bindProbes() {
        bindProbe(COURIER_ASSIGNED_PROBE, RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED);
        bindProbe(STATUS_PROBE, RabbitMQConfig.ROUTING_KEY_PARCEL_STATUS_CHANGED);
    }

    @Test
    @DisplayName("Before pickup: /reassign succeeds and publishes courier.assigned but no parcel.status.changed")
    void reassignmentBeforePickupSucceeds() throws Exception {
        UUID courierA = availableCourier();
        UUID courierB = availableCourier();
        UUID orderId = assignedOrder(courierA);

        MvcResult result = reassign(orderId, courierA, courierB);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertAssignment(orderId, courierB, AssignmentStatus.REASSIGNED);
        assertWorkloads(courierA, 0, courierB, 1);
        assertCourierAssignedEvents(orderId, 1);
        assertThat(rabbitTemplate.receive(STATUS_PROBE, 2_000))
                .as("reassignment does not publish parcel.status.changed").isNull();
    }

    @Test
    @DisplayName("A PICKED_UP event from delivery-execution blocks reassignment with 409 Reassignment Not Allowed")
    void pickupEventBlocksReassignment() throws Exception {
        UUID courierA = availableCourier();
        UUID courierB = availableCourier();
        UUID orderId = assignedOrder(courierA);

        publishDeliveryStatus(orderId, PublicOrderStatus.PICKED_UP, Instant.now());
        await().atMost(Duration.ofSeconds(15)).until(() -> pickedUpAt(orderId) != null);

        MvcResult result = reassign(orderId, courierA, courierB);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asText()).isEqualTo("Reassignment Not Allowed");
        assertAssignment(orderId, courierA, AssignmentStatus.ASSIGNED);
        assertWorkloads(courierA, 1, courierB, 0);
        assertCourierAssignedEvents(orderId, 0);
    }

    @Test
    @DisplayName("Out-of-order and duplicate post-pickup events record pickup once and keep the first time")
    void outOfOrderAndDuplicatePickupEventsRecordOnce() {
        UUID orderId = assignedOrder(availableCourier());
        Instant inTransitAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);

        publishDeliveryStatus(orderId, PublicOrderStatus.IN_TRANSIT, inTransitAt);
        publishDeliveryStatus(orderId, PublicOrderStatus.PICKED_UP, inTransitAt.minusSeconds(30));
        publishDeliveryStatus(orderId, PublicOrderStatus.IN_TRANSIT, inTransitAt);
        awaitEarlierEventsProcessed();

        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(assignment.getPickedUpAt()).as("first processed event wins").isEqualTo(inTransitAt);
        assertThat(assignment.getVersion()).as("written exactly once").isEqualTo(1L);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    @DisplayName("A pickup event delayed until after DELIVERED leaves the completed assignment untouched")
    void delayedPickupEventAfterDeliveredIsIgnored() {
        UUID orderId = assignedOrder(availableCourier());

        publishDeliveryStatus(orderId, PublicOrderStatus.DELIVERED, Instant.now());
        await().atMost(Duration.ofSeconds(15)).until(() ->
                assignmentRepository.findByOrderId(orderId).orElseThrow().getStatus() == AssignmentStatus.COMPLETED);
        publishDeliveryStatus(orderId, PublicOrderStatus.IN_TRANSIT, Instant.now().minusSeconds(300));
        awaitEarlierEventsProcessed();

        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getPickedUpAt()).isNull();
    }

    @RepeatedTest(5)
    @DisplayName("Race, pickup update first: the reassignment loses with 409 and nothing changes")
    void pickupCommittedFirstRejectsTheRacingReassignment() throws Exception {
        RaceSetup race = new RaceSetup();

        int httpStatus = race.run(true);

        assertThat(httpStatus).as("reassignment hits the optimistic-lock conflict").isEqualTo(409);
        assertAssignment(race.orderId, race.courierA, AssignmentStatus.ASSIGNED);
        assertWorkloads(race.courierA, 1, race.courierB, 0);
        assertCourierAssignedEvents(race.orderId, 0);
        assertNoFurtherReassignment(race.orderId, race.courierA);
    }

    @RepeatedTest(5)
    @DisplayName("Race, reassignment first: it succeeds, the pickup update is retried and still recorded")
    void reassignmentCommittedFirstThenPickupIsRetriedAndRecorded() throws Exception {
        RaceSetup race = new RaceSetup();

        int httpStatus = race.run(false);

        assertThat(httpStatus).isEqualTo(200);
        assertAssignment(race.orderId, race.courierB, AssignmentStatus.REASSIGNED);
        assertWorkloads(race.courierA, 0, race.courierB, 1);
        assertCourierAssignedEvents(race.orderId, 1);
        assertNoFurtherReassignment(race.orderId, race.courierB);
    }

    /**
     * Two writers of the same assignment row. The test holds the row lock, starts
     * the first writer and waits until its versioned UPDATE is blocked, then starts
     * the second and waits for it too. PostgreSQL queues row-lock waiters in
     * arrival order, so releasing the lock lets the first writer commit and the
     * second fail its version check. A failed listener update is retried by the
     * bounded listener retry; a failed HTTP request returns 409.
     */
    private final class RaceSetup {
        final UUID courierA = availableCourier();
        final UUID courierB = availableCourier();
        final UUID orderId = assignedOrder(courierA);

        int run(boolean pickupFirst) throws Exception {
            ExecutorService dispatcher = Executors.newSingleThreadExecutor();
            try {
                Future<MvcResult> reassignment = new TransactionTemplate(transactionManager).execute(status -> {
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM dispatch_assignments WHERE order_id = ? FOR UPDATE", Long.class, orderId);
                    Future<MvcResult> request;
                    if (pickupFirst) {
                        publishDeliveryStatus(orderId, PublicOrderStatus.PICKED_UP, Instant.now());
                        awaitBlockedAssignmentUpdates(1);
                        request = dispatcher.submit(() -> reassign(orderId, courierA, courierB));
                    } else {
                        request = dispatcher.submit(() -> reassign(orderId, courierA, courierB));
                        awaitBlockedAssignmentUpdates(1);
                        publishDeliveryStatus(orderId, PublicOrderStatus.PICKED_UP, Instant.now());
                    }
                    awaitBlockedAssignmentUpdates(2);
                    return request;
                }); // committing the lock-holding transaction releases both writers in arrival order

                int httpStatus = reassignment.get(30, TimeUnit.SECONDS).getResponse().getStatus();
                await().atMost(Duration.ofSeconds(20)).until(() -> pickedUpAt(orderId) != null);
                return httpStatus;
            } finally {
                dispatcher.shutdownNow();
            }
        }
    }

    private void awaitBlockedAssignmentUpdates(int expected) {
        await().atMost(Duration.ofSeconds(20)).until(() -> blockedAssignmentUpdates() == expected);
    }

    private void assertNoFurtherReassignment(UUID orderId, UUID currentCourier) throws Exception {
        MvcResult next = reassign(orderId, currentCourier, availableCourier());
        assertThat(next.getResponse().getStatus())
                .as("once pickup is recorded no further reassignment is accepted").isEqualTo(409);
        assertThat(body(next).get("error").asText()).isEqualTo("Reassignment Not Allowed");
    }

    private MvcResult reassign(UUID orderId, UUID expectedCourier, UUID newCourier) throws Exception {
        return mockMvc.perform(post("/api/v1/dispatch/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReassignCourierRequest(
                                orderId, expectedCourier, newCourier, "IT_SUPERVISOR", null))))
                .andReturn();
    }

    /** Same shape delivery-execution-service publishes: its own tracking number, orderId always set. */
    private void publishDeliveryStatus(UUID orderId, PublicOrderStatus status, Instant timestamp) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_PARCEL_STATUS,
                new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(),
                        "SD-" + orderId.toString().substring(0, 8).toUpperCase(), orderId, null,
                        PublicOrderStatus.PICKUP_ASSIGNED, status, null,
                        SriLankanDistrict.COLOMBO, SriLankanDistrict.GAMPAHA, null, timestamp));
    }

    /**
     * The status listener has a single consumer and processes messages in order, so
     * once a marker event published last has been applied, every earlier event has
     * been processed too.
     */
    private void awaitEarlierEventsProcessed() {
        UUID markerOrder = assignedOrder(availableCourier());
        publishDeliveryStatus(markerOrder, PublicOrderStatus.PICKED_UP, Instant.now());
        await().atMost(Duration.ofSeconds(15)).until(() -> pickedUpAt(markerOrder) != null);
    }

    private Instant pickedUpAt(UUID orderId) {
        return assignmentRepository.findByOrderId(orderId).orElseThrow().getPickedUpAt();
    }

    private int blockedAssignmentUpdates() {
        Integer blocked = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database()
                  AND wait_event_type = 'Lock'
                  AND query ILIKE 'update dispatch_assignments%'
                """, Integer.class);
        return blocked == null ? 0 : blocked;
    }

    private void assertAssignment(UUID orderId, UUID courierId, AssignmentStatus status) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(assignment.getCourierId()).isEqualTo(courierId);
        assertThat(assignment.getStatus()).isEqualTo(status);
    }

    private void assertWorkloads(UUID courierA, int expectedA, UUID courierB, int expectedB) {
        assertThat(courierShiftRepository.findByCourierId(courierA).orElseThrow().getActiveParcelsCount())
                .isEqualTo(expectedA);
        assertThat(courierShiftRepository.findByCourierId(courierB).orElseThrow().getActiveParcelsCount())
                .isEqualTo(expectedB);
    }

    private void assertCourierAssignedEvents(UUID orderId, int expected) throws Exception {
        int received = 0;
        Message message;
        while ((message = rabbitTemplate.receive(COURIER_ASSIGNED_PROBE, received < expected ? 10_000 : 2_000)) != null) {
            assertThat(objectMapper.readTree(message.getBody()).get("orderId").asText()).isEqualTo(orderId.toString());
            received++;
        }
        assertThat(received).as("dispatch.courier.assigned events for the order").isEqualTo(expected);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void bindProbe(String queueName, String routingKey) {
        Queue probe = QueueBuilder.nonDurable(queueName).build();
        rabbitAdmin.declareQueue(probe);
        rabbitAdmin.declareBinding(BindingBuilder.bind(probe)
                .to(new TopicExchange(RabbitMQConfig.EXCHANGE_NAME, true, false))
                .with(routingKey));
        rabbitAdmin.purgeQueue(queueName, false);
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
        courier.setActiveParcelsCount(courier.getActiveParcelsCount() + 1);
        courierShiftRepository.saveAndFlush(courier);
        return orderId;
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
}
