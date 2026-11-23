package com.swiftdrop.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.support.TestCallers;
import com.swiftdrop.dispatch.support.WorkingHoursClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.swiftdrop.dispatch.support.TestCallers.dispatcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HTTP error handling that depends on real database and transaction behaviour:
 * rejected shift ends, unique-key races and courier-record optimistic-lock races
 * between an assignment and that courier's location ping. Races are ordered with
 * a row lock (no sleeps): PostgreSQL releases blocked writers in arrival order.
 */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@Import(WorkingHoursClock.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class CourierShiftErrorHandlingIT {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @BeforeEach
    void ignoreFrameworkStartupCalls() {
        clearInvocations(rabbitTemplate);
    }

    @Test
    @DisplayName("Ending a shift with active parcels returns 409 Shift Conflict and leaves the shift unchanged")
    void endShiftWithActiveParcelsIsRejectedWithoutChanges() throws Exception {
        UUID courierId = courier(2, ShiftStatus.BUSY);
        CourierShift before = courierShiftRepository.findByCourierId(courierId).orElseThrow();

        MvcResult result = mockMvc.perform(post("/api/v1/dispatch/couriers/shifts/{courierId}/end", courierId)
                        .with(TestCallers.courier(courierId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = body(result);
        assertThat(body.get("error").asText()).isEqualTo("Shift Conflict");
        assertThat(body.get("message").asText()).contains("2 active parcel(s)");
        CourierShift after = courierShiftRepository.findByCourierId(courierId).orElseThrow();
        assertThat(after.getShiftStatus()).isEqualTo(ShiftStatus.BUSY);
        assertThat(after.getActiveParcelsCount()).isEqualTo(2);
        assertThat(after.getShiftEndedAt()).isNull();
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
    }

    @Test
    @DisplayName("A first-time shift start losing a unique-key race returns a safe 409 and keeps the existing row")
    void concurrentFirstShiftStartReturnsSafeConflict() throws Exception {
        UUID courierId = UUID.randomUUID();
        ExecutorService courierApp = Executors.newSingleThreadExecutor();
        // A shift needs the courier's registered vehicle; commit it first so only the shift insert races.
        jdbcTemplate.update("INSERT INTO courier_profiles (courier_id, district, vehicle_type, plate_number, "
                + "vehicle_model, vehicle_colour, licence_number, max_capacity) "
                + "VALUES (?, 'COLOMBO', 'MOTORCYCLE', ?, 'Honda Dio', 'Black', 'B1234567', 5)",
                courierId, "CAB-" + (1000 + Math.abs(courierId.hashCode() % 9000)));
        try {
            // An uncommitted row for the same courier makes the request's INSERT block
            // on the unique index, then fail with a real unique violation (23505).
            Future<MvcResult> start = new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.update("INSERT INTO courier_shifts (courier_id, courier_name, current_district, shift_status) "
                        + "VALUES (?, 'Registered first', 'KANDY', 'AVAILABLE')", courierId);
                Future<MvcResult> request = courierApp.submit(() -> mockMvc.perform(
                                post("/api/v1/dispatch/couriers/shifts/start").with(TestCallers.courier(courierId))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new StartShiftRequest(courierId,
                                                "Registered second", null))))
                        .andReturn());
                awaitBlocked("insert into courier_shifts%", 1);
                return request;
            });

            MvcResult result = start.get(30, TimeUnit.SECONDS);

            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            JsonNode body = body(result);
            assertThat(body.get("error").asText()).isEqualTo("Data Conflict");
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContainIgnoringCase("duplicate key")
                    .doesNotContain("courier_shifts", "23505");
            CourierShift kept = courierShiftRepository.findByCourierId(courierId).orElseThrow();
            assertThat(kept.getCourierName()).isEqualTo("Registered first");
            assertThat(kept.getCurrentDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        } finally {
            courierApp.shutdownNow();
        }
    }

    @Test
    @DisplayName("A courier status update commits first: the racing assignment is retried and succeeds with one event")
    void assignmentRacingAnEarlierStatusUpdateIsRetried(CapturedOutput output) throws Exception {
        UUID courierId = courier(0, ShiftStatus.AVAILABLE);
        UUID orderId = unassignedOrder();

        RaceResult race = raceStatusUpdateThenAssignment(courierId, orderId);

        assertThat(race.assignStatus).isEqualTo(200);
        assertThat(race.statusUpdateStatus).isEqualTo(200);
        assertThat(output.getOut()).contains("Assignment lost a courier record update race");
        assertConsistentAfterRace(courierId, orderId);
    }

    private record RaceResult(int assignStatus, int statusUpdateStatus) {
    }

    private RaceResult raceStatusUpdateThenAssignment(UUID courierId, UUID orderId) throws Exception {
        ExecutorService clients = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult>[] requests = new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM courier_shifts WHERE courier_id = ? FOR UPDATE", Long.class, courierId);
                Future<MvcResult> first = clients.submit(() -> updateStatus(courierId));
                awaitBlocked("update courier_shifts%", 1);
                Future<MvcResult> second = clients.submit(() -> assign(orderId, courierId));
                awaitBlocked("update courier_shifts%", 2);
                @SuppressWarnings("unchecked")
                Future<MvcResult>[] ordered = new Future[]{first, second};
                return ordered;
            }); // releasing the row lock lets the first writer commit and the second hit the version check

            MvcResult statusUpdate = requests[0].get(30, TimeUnit.SECONDS);
            MvcResult assign = requests[1].get(30, TimeUnit.SECONDS);
            return new RaceResult(assign.getResponse().getStatus(), statusUpdate.getResponse().getStatus());
        } finally {
            clients.shutdownNow();
        }
    }

    private void assertConsistentAfterRace(UUID courierId, UUID orderId) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getCourierId()).isEqualTo(courierId);

        CourierShift courier = courierShiftRepository.findByCourierId(courierId).orElseThrow();
        assertThat(courier.getActiveParcelsCount()).as("workload incremented exactly once").isEqualTo(1);
        assertThat(courier.getShiftStatus()).as("status update kept").isEqualTo(ShiftStatus.AVAILABLE);
        assertThat(courier.getLastHeartbeatAt()).isNotNull();

        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED), isA(CourierAssignedEvent.class));
    }

    private MvcResult assign(UUID orderId, UUID courierId) throws Exception {
        return mockMvc.perform(post("/api/v1/dispatch/assign").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ManualAssignRequest(orderId, courierId, "IT_DISPATCHER", null))))
                .andReturn();
    }

    private MvcResult updateStatus(UUID courierId) throws Exception {
        return mockMvc.perform(put("/api/v1/dispatch/couriers/shifts/status").with(TestCallers.courier(courierId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShiftStatusRequest(courierId, ShiftStatus.AVAILABLE))))
                .andReturn();
    }

    private void awaitBlocked(String statementPattern, int expected) {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Integer blocked = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock' AND query ILIKE ?
                    """, Integer.class, statementPattern);
            return blocked != null && blocked == expected;
        });
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID courier(int activeParcels, ShiftStatus status) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(UUID.randomUUID());
        courier.setCourierName("IT Courier");
        courier.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        courier.setShiftStatus(status);
        courier.setActiveParcelsCount(activeParcels);
        courier.setMaxCapacity(5);
        courier.setLastHeartbeatAt(Instant.now());
        return courierShiftRepository.saveAndFlush(courier).getCourierId();
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
}
