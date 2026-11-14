package com.swiftdrop.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Two dispatchers assign the same unassigned order to different couriers through
 * the real HTTP endpoint, PostgreSQL and RabbitMQ.
 *
 * <p>The race is made deterministic with a database row lock rather than sleeps.
 * The test holds {@code SELECT ... FOR UPDATE} on the assignment row, starts both
 * requests, and waits until PostgreSQL reports both of their versioned
 * {@code UPDATE} statements blocked on that lock. By then both requests have read
 * the order as UNASSIGNED, so releasing the lock forces the genuine optimistic-lock
 * conflict: one commit wins and the other is rejected with 409.</p>
 */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentAssignmentIT {

    private static final String COURIER_ASSIGNED_PROBE_QUEUE = "it.concurrent-assignment.courier-assigned";

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
        // Only the HTTP assignment path is under test; the service's own consumers stay idle.
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @BeforeEach
    void bindCourierAssignedProbe() {
        Queue probe = QueueBuilder.nonDurable(COURIER_ASSIGNED_PROBE_QUEUE).build();
        rabbitAdmin.declareQueue(probe);
        rabbitAdmin.declareBinding(BindingBuilder.bind(probe)
                .to(new TopicExchange(RabbitMQConfig.EXCHANGE_NAME, true, false))
                .with(RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED));
        rabbitAdmin.purgeQueue(COURIER_ASSIGNED_PROBE_QUEUE, false);
    }

    @Test
    @DisplayName("Two dispatchers racing to assign one order: one 200, one 409, one consistent assignment")
    void concurrentAssignmentsProduceExactlyOneWinner() throws Exception {
        UUID orderId = unassignedOrder();
        UUID courierA = availableCourier();
        UUID courierB = availableCourier();
        ExecutorService dispatchers = Executors.newFixedThreadPool(2);

        try {
            List<Future<MvcResult>> requests = new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM dispatch_assignments WHERE order_id = ? FOR UPDATE", Long.class, orderId);

                Future<MvcResult> first = dispatchers.submit(() -> assign(orderId, courierA, "DISPATCHER_A"));
                Future<MvcResult> second = dispatchers.submit(() -> assign(orderId, courierB, "DISPATCHER_B"));

                await().atMost(Duration.ofSeconds(20))
                        .until(() -> blockedAssignmentUpdates() == 2);
                return List.of(first, second);
            }); // committing the lock-holding transaction releases both requests

            MvcResult first = requests.get(0).get(30, TimeUnit.SECONDS);
            MvcResult second = requests.get(1).get(30, TimeUnit.SECONDS);
            assertThat(List.of(first.getResponse().getStatus(), second.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);

            MvcResult winner = first.getResponse().getStatus() == 200 ? first : second;
            MvcResult loser = winner == first ? second : first;
            UUID winningCourier = UUID.fromString(body(winner).at("/data/courierId").asText());
            UUID losingCourier = winningCourier.equals(courierA) ? courierB : courierA;

            JsonNode conflict = body(loser);
            assertThat(conflict.get("status").asInt()).isEqualTo(409);
            assertThat(conflict.get("error").asText()).isEqualTo("Conflict");

            assertOnlyAssignment(orderId, winningCourier);
            assertWorkload(winningCourier, 1);
            assertWorkload(losingCourier, 0);
            assertExactlyOneCourierAssignedEvent(orderId, winningCourier);
        } finally {
            dispatchers.shutdownNow();
        }
    }

    @Test
    @DisplayName("A second dispatcher arriving after the first commit gets 409 and changes nothing")
    void sequentialSecondAssignmentIsRejected() throws Exception {
        UUID orderId = unassignedOrder();
        UUID courierA = availableCourier();
        UUID courierB = availableCourier();

        assertThat(assign(orderId, courierA, "DISPATCHER_A").getResponse().getStatus()).isEqualTo(200);

        MvcResult second = assign(orderId, courierB, "DISPATCHER_B");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(second).get("error").asText()).isEqualTo("Assignment Conflict");

        assertOnlyAssignment(orderId, courierA);
        assertWorkload(courierA, 1);
        assertWorkload(courierB, 0);
        assertExactlyOneCourierAssignedEvent(orderId, courierA);
    }

    private MvcResult assign(UUID orderId, UUID courierId, String dispatcher) throws Exception {
        return mockMvc.perform(post("/api/v1/dispatch/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ManualAssignRequest(orderId, courierId, dispatcher, null))))
                .andReturn();
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

    private void assertOnlyAssignment(UUID orderId, UUID expectedCourier) {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dispatch_assignments WHERE order_id = ?", Integer.class, orderId);
        assertThat(rows).isEqualTo(1);

        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getCourierId()).isEqualTo(expectedCourier);
        assertThat(assignment.getVersion()).isEqualTo(1L);
    }

    private void assertWorkload(UUID courierId, int expectedActiveParcels) {
        CourierShift courier = courierShiftRepository.findByCourierId(courierId).orElseThrow();
        assertThat(courier.getActiveParcelsCount()).isEqualTo(expectedActiveParcels);
        assertThat(courier.getShiftStatus()).isEqualTo(ShiftStatus.AVAILABLE);
    }

    private void assertExactlyOneCourierAssignedEvent(UUID orderId, UUID expectedCourier) throws Exception {
        Message event = rabbitTemplate.receive(COURIER_ASSIGNED_PROBE_QUEUE, 10_000);
        assertThat(event).as("one dispatch.courier.assigned event").isNotNull();
        JsonNode payload = objectMapper.readTree(event.getBody());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(payload.get("courierId").asText()).isEqualTo(expectedCourier.toString());

        // Every send happens synchronously in the committing request thread, which
        // has already returned; a bounded receive confirms nothing else was published.
        assertThat(rabbitTemplate.receive(COURIER_ASSIGNED_PROBE_QUEUE, 2_000))
                .as("no second dispatch.courier.assigned event").isNull();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
