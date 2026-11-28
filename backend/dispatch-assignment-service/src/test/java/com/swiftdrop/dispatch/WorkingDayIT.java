package com.swiftdrop.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.support.TestCallers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The working calendar and the shift rules over HTTP, against the real schema. Today is Tuesday 24 November 2026. */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WorkingDayIT {

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

    private final RequestPostProcessor admin = TestCallers.as(UserRole.SYSTEM_ADMIN);

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM closed_days");
        jdbcTemplate.update("UPDATE working_hours SET working_days = 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY', "
                + "opens_at = '08:00', closes_at = '17:00', shift_from = '07:30', late_after = '09:00', "
                + "break_from = '12:00', break_until = '14:30', break_minutes = 45, pickup_cutoff = '15:00', "
                + "same_day_cutoff = '11:00'");
        jdbcTemplate.update("DELETE FROM courier_shifts");
        jdbcTemplate.update("DELETE FROM courier_profiles");
    }

    @Test
    @DisplayName("An admin closes and reopens holidays; Sundays, past days and repeats are refused; others only read")
    void adminKeepsTheHolidays() throws Exception {
        JsonNode added = data(send(post("/api/v1/dispatch/calendar/closed-days"), admin,
                Map.of("day", "2026-12-04", "name", " Unduvap Poya Day ")), 200);
        assertThat(added).hasSize(1);
        assertThat(added.at("/0/name").asText()).isEqualTo("Unduvap Poya Day");

        status(send(post("/api/v1/dispatch/calendar/closed-days"), admin, Map.of("day", "2026-12-04", "name", "Again")), 400);
        status(send(post("/api/v1/dispatch/calendar/closed-days"), admin, Map.of("day", "2026-11-29", "name", "Sunday")), 400);
        status(send(post("/api/v1/dispatch/calendar/closed-days"), admin, Map.of("day", "2026-11-23", "name", "Past")), 400);
        status(send(post("/api/v1/dispatch/calendar/closed-days"), TestCallers.dispatcher(),
                Map.of("day", "2026-12-05", "name", "Mine")), 403);

        JsonNode today = data(send(get("/api/v1/dispatch/calendar/today"), TestCallers.courier(UUID.randomUUID()), null), 200);
        assertThat(today.get("date").asText()).isEqualTo("2026-11-24");
        assertThat(today.get("open").asBoolean()).isTrue();
        assertThat(today.get("shiftCanStart").asBoolean()).isTrue();
        assertThat(today.get("breakMinutes").asInt()).isEqualTo(45);
        assertThat(today.get("closedDays")).hasSize(1);
        status(send(get("/api/v1/dispatch/calendar/today"), TestCallers.as(UserRole.CUSTOMER), null), 403);

        // The order service reads the holidays for its delivery promise.
        JsonNode internal = data(send(get("/internal/v1/calendar/closed-days?from=2026-12-01&to=2026-12-31"),
                TestCallers.as(UserRole.SYSTEM_ADMIN), null), 200);
        assertThat(internal).hasSize(1);
        assertThat(internal.get(0).asText()).isEqualTo("2026-12-04");

        assertThat(data(send(delete("/api/v1/dispatch/calendar/closed-days/2026-12-04"), admin, null), 200)).isEmpty();
    }

    @Test
    @DisplayName("An admin changes the working hours and restores the standard ones; others cannot")
    void adminEditsTheWorkingHours() throws Exception {
        Map<String, Object> hours = new java.util.HashMap<>(Map.of(
                "workingDays", List.of("MONDAY", "WEDNESDAY", "FRIDAY"),
                "opensAt", "09:00", "closesAt", "18:00", "shiftFrom", "08:30", "lateAfter", "09:30",
                "breakFrom", "12:30", "breakUntil", "14:00", "breakMinutes", 30,
                "pickupCutoff", "16:00", "sameDayCutoff", "10:30"));

        status(send(put("/api/v1/dispatch/calendar/hours"), TestCallers.dispatcher(), hours), 403);

        // Tuesday 24 November is no longer a working day.
        JsonNode today = data(send(put("/api/v1/dispatch/calendar/hours"), admin, hours), 200);
        assertThat(today.get("open").asBoolean()).isFalse();
        assertThat(today.get("closedReason").asText()).isEqualTo("Tuesday");
        assertThat(today.get("nextWorkingDay").asText()).isEqualTo("2026-11-25");
        assertThat(today.get("opensAt").asText()).startsWith("09:00");
        assertThat(today.get("breakMinutes").asInt()).isEqualTo(30);
        assertThat(today.get("sameDayCutoff").asText()).startsWith("10:30");
        assertThat(today.get("workingDays")).hasSize(3);

        // The order service reads the new cut-offs.
        JsonNode rules = data(send(get("/internal/v1/calendar/rules"), admin, null), 200);
        assertThat(rules.get("sameDayCutoff").asText()).startsWith("10:30");
        assertThat(rules.get("pickupCutoff").asText()).startsWith("16:00");

        // A Tuesday holiday is refused: Tuesdays are closed anyway.
        status(send(post("/api/v1/dispatch/calendar/closed-days"), admin, Map.of("day", "2026-12-01", "name", "X")), 400);

        hours.put("closesAt", "21:00");
        assertThat(message(send(put("/api/v1/dispatch/calendar/hours"), admin, hours), 400)).contains("20:00");
        hours.put("closesAt", "18:00");
        hours.put("breakUntil", "12:00");
        status(send(put("/api/v1/dispatch/calendar/hours"), admin, hours), 400);
        hours.put("workingDays", List.of());
        status(send(put("/api/v1/dispatch/calendar/hours"), admin, hours), 400);

        status(send(delete("/api/v1/dispatch/calendar/hours"), TestCallers.dispatcher(), null), 403);
        JsonNode standard = data(send(delete("/api/v1/dispatch/calendar/hours"), admin, null), 200);
        assertThat(standard.get("open").asBoolean()).isTrue();
        assertThat(standard.get("opensAt").asText()).startsWith("08:00");
        assertThat(standard.get("workingDays")).hasSize(6);
    }

    @Test
    @DisplayName("On a holiday nobody starts a shift; a running shift ends only through End shift")
    void shiftRulesOverHttp() throws Exception {
        UUID courierId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO courier_profiles (courier_id, district, vehicle_type, plate_number, "
                + "vehicle_model, vehicle_colour, licence_number, max_capacity) "
                + "VALUES (?, 'COLOMBO', 'MOTORCYCLE', 'CAB-4321', 'Honda Dio', 'Black', 'B1234567', 5)", courierId);
        jdbcTemplate.update("INSERT INTO closed_days (day, name) VALUES ('2026-11-24', 'Il Poya Day')");
        StartShiftRequest start = new StartShiftRequest(courierId, "Nuwan Perera", null);

        assertThat(message(send(post("/api/v1/dispatch/couriers/shifts/start"), TestCallers.courier(courierId), start), 409))
                .contains("Il Poya Day").contains("Wednesday 25 November");

        jdbcTemplate.update("DELETE FROM closed_days");
        JsonNode shift = data(send(post("/api/v1/dispatch/couriers/shifts/start"), TestCallers.courier(courierId), start), 200);
        assertThat(shift.get("shiftStatus").asText()).isEqualTo("AVAILABLE");
        assertThat(shift.get("startedLate").asBoolean()).isTrue();

        assertThat(message(send(put("/api/v1/dispatch/couriers/shifts/status"), TestCallers.courier(courierId),
                new UpdateShiftStatusRequest(courierId, ShiftStatus.OFFLINE)), 409)).contains("End the shift");
        assertThat(message(send(put("/api/v1/dispatch/couriers/shifts/status"), TestCallers.courier(courierId),
                new UpdateShiftStatusRequest(courierId, ShiftStatus.ON_BREAK)), 409)).contains("12:00");
        assertThat(jdbcTemplate.queryForObject("SELECT shift_status FROM courier_shifts WHERE courier_id = ?",
                String.class, courierId)).isEqualTo("AVAILABLE");
    }

    private MvcResult send(MockHttpServletRequestBuilder request, RequestPostProcessor caller, Object body) throws Exception {
        request.with(caller);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode data(MvcResult result, int status) throws Exception {
        status(result, status);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String message(MvcResult result, int status) throws Exception {
        status(result, status);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText();
    }

    private static void status(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
    }
}
