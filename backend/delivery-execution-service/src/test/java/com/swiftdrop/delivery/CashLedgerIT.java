package com.swiftdrop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import com.swiftdrop.delivery.statemachine.guards.ProofOfDeliveryGuard;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The cash from the sender's hand to the bank, over HTTP and a real PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class CashLedgerIT {

    private record Person(UUID id, UserRole role, SriLankanDistrict district) {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private OrderJobClient orderJobClient;

    @MockitoBean
    private ProofOfDeliveryGuard proofOfDeliveryGuard;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeliverySessionRepository sessions;

    @Autowired
    private DeliveryExecutionService deliveryService;

    private final Person courier = new Person(UUID.randomUUID(), UserRole.COURIER, SriLankanDistrict.COLOMBO);
    private final Person kandyCourier = new Person(UUID.randomUUID(), UserRole.COURIER, SriLankanDistrict.KANDY);
    private final Person dispatcher = new Person(UUID.randomUUID(), UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
    private final Person kandyDispatcher = new Person(UUID.randomUUID(), UserRole.DISPATCHER, SriLankanDistrict.KANDY);
    private final Person admin = new Person(UUID.randomUUID(), UserRole.SYSTEM_ADMIN, null);

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM cash_entries");
        jdbc.update("DELETE FROM cash_handovers");
        jdbc.update("DELETE FROM cash_deposits");
        jdbc.update("DELETE FROM delivery_sessions");
        when(proofOfDeliveryGuard.isSatisfied(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("A pickup needs the exact fee confirmed, and records it once under the pickup district")
    void pickupRecordsTheFee() throws Exception {
        DeliverySession job = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, "670.00");

        assertThat(message(step(job, courier, "PICKED_UP", null), 409)).contains("LKR 670.00 from the sender");
        assertThat(message(step(job, courier, "PICKED_UP", "350.00"), 409)).contains("LKR 670.00");
        assertThat(state(job)).isEqualTo(DeliveryState.ASSIGNED);

        ok(step(job, courier, "PICKED_UP", "670"), 200);

        assertThat(state(job)).isEqualTo(DeliveryState.PICKED_UP);
        assertThat(jdbc.queryForMap("SELECT district, amount FROM cash_entries"))
                .containsEntry("district", "COLOMBO")
                .containsEntry("amount", new BigDecimal("670.00"));
        status(step(job, courier, "PICKED_UP", "670"), 409);
        JsonNode mine = ok(get("/api/v1/cash/me"), courier, 200);
        assertThat(mine.at("/data/inHand").decimalValue()).isEqualByComparingTo("670");
    }

    @Test
    @DisplayName("A delivery takes no money: the recipient never pays")
    void deliveryTakesNoMoney() throws Exception {
        DeliverySession job = job(kandyCourier, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, "670.00");
        job.setCurrentState(DeliveryState.OUT_FOR_DELIVERY);
        sessions.saveAndFlush(job);

        ok(step(job, kandyCourier, "DELIVERED", null), 200);

        assertThat(entries()).isZero();
        assertThat(ok(get("/api/v1/cash/me"), kandyCourier, 200).at("/data/inHand").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Nothing due needs no confirmation; a cancelled order is never picked up")
    void nothingDueAndCancelled() throws Exception {
        DeliverySession free = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, "0.00");
        ok(step(free, courier, "PICKED_UP", null), 200);
        assertThat(entries()).isZero();

        DeliverySession gone = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, "350.00");
        order(gone, true, "350.00");
        assertThat(message(step(gone, courier, "PICKED_UP", "350.00"), 409)).contains("cancelled");
        assertThat(entries()).isZero();
    }

    @Test
    @DisplayName("Without a verified caller, or for another courier's parcel, no step and no cash")
    void stepsNeedTheRightCaller() throws Exception {
        DeliverySession job = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, "350.00");

        status(mockMvc.perform(post("/api/v1/deliveries/{id}/transition", job.getDeliveryId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetState\":\"PICKED_UP\",\"cashCollected\":350}")).andReturn(), 401);
        status(step(job, kandyCourier, "PICKED_UP", "350.00"), 403);
        assertThat(entries()).isZero();
    }

    @Test
    @DisplayName("Collect, hand over, count with a shortage, bank and confirm")
    void fromCourierToBank() throws Exception {
        pickUp("350.00");
        pickUp("420.00");

        JsonNode handover = ok(post("/api/v1/cash/handovers"), courier, 201).get("data");
        assertThat(handover.get("expectedAmount").decimalValue()).isEqualByComparingTo("770");
        assertThat(ok(get("/api/v1/cash/me"), courier, 200).at("/data/inHand").decimalValue()).isEqualByComparingTo("0");
        status(send(post("/api/v1/cash/handovers"), courier), 409);
        String id = handover.get("handoverId").asText();
        assertThat(ok(get("/api/v1/cash/handovers/" + id + "/entries"), courier, 200).get("data")).hasSize(2);

        // Only the dispatcher of that district counts, and a difference needs a note.
        status(count(id, kandyDispatcher, "770", null), 403);
        status(count(id, admin, "770", null), 403);
        status(count(id, courier, "770", null), 403);
        assertThat(message(count(id, dispatcher, "700", " "), 409)).contains("note");
        JsonNode counted = ok(count(id, dispatcher, "700.00", "Rs. 70 missing"), 200).get("data");
        assertThat(counted.get("difference").decimalValue()).isEqualByComparingTo("-70");
        status(count(id, dispatcher, "770", null), 409);

        JsonNode desk = ok(get("/api/v1/cash/desk"), dispatcher, 200).get("data");
        assertThat(desk.get("hubCash").decimalValue()).isEqualByComparingTo("700");
        assertThat(desk.get("toCount")).isEmpty();

        // The amount shown to the dispatcher must still be right.
        status(deposit(dispatcher, "650.00"), 409);
        status(deposit(kandyDispatcher, "700.00"), 409);
        status(deposit(admin, "700.00"), 403);
        String depositId = ok(deposit(dispatcher, "700.00"), 201).at("/data/depositId").asText();
        assertThat(ok(get("/api/v1/cash/desk"), dispatcher, 200).at("/data/hubCash").decimalValue()).isEqualByComparingTo("0");

        // Nobody confirms their own deposit.
        status(send(post("/api/v1/cash/deposits/" + depositId + "/confirm"), dispatcher), 403);
        assertThat(ok(post("/api/v1/cash/deposits/" + depositId + "/confirm"), admin, 200).at("/data/status").asText())
                .isEqualTo("CONFIRMED");
        status(send(post("/api/v1/cash/deposits/" + depositId + "/confirm"), admin), 409);
    }

    @Test
    @DisplayName("A rejected deposit puts its cash back at the hub")
    void rejectedDepositReturnsTheCash() throws Exception {
        String id = handOver("350.00");
        ok(count(id, dispatcher, "350.00", null), 200);
        String depositId = ok(deposit(dispatcher, "350.00"), 201).at("/data/depositId").asText();

        ok(send(post("/api/v1/cash/deposits/" + depositId + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No such slip\"}"), admin), 200);

        assertThat(ok(get("/api/v1/cash/desk"), dispatcher, 200).at("/data/hubCash").decimalValue()).isEqualByComparingTo("350");
        ok(deposit(dispatcher, "350.00"), 201);
    }

    @Test
    @DisplayName("A fee is refunded once, from counted hub cash, only for a parcel lost or damaged")
    void refundRules() throws Exception {
        DeliverySession damaged = pickUp("350.00");
        DeliverySession refused = pickUp("420.00");
        fail(damaged, "DAMAGED_IN_TRANSIT");
        fail(refused, "RECIPIENT_REJECTED");

        // Not counted yet: there is no cash at the hub to pay from.
        status(refund(dispatcher, damaged), 409);
        String id = ok(post("/api/v1/cash/handovers"), courier, 201).at("/data/handoverId").asText();
        ok(count(id, dispatcher, "770", null), 200);

        assertThat(message(refund(dispatcher, refused), 409)).contains("lost or damaged");
        status(refund(kandyDispatcher, damaged), 404);
        status(refund(courier, damaged), 403);
        ok(refund(dispatcher, damaged), 201);
        assertThat(message(refund(dispatcher, damaged), 409)).contains("already refunded");

        assertThat(ok(get("/api/v1/cash/desk"), dispatcher, 200).at("/data/hubCash").decimalValue()).isEqualByComparingTo("420");
        ok(deposit(dispatcher, "420.00"), 201);
        assertThat(ok(get("/api/v1/cash/desk"), dispatcher, 200).at("/data/hubCash").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("The desk shows who holds cash, only for the dispatcher's district; an admin sees every district")
    void deskIsPerDistrict() throws Exception {
        pickUp("350.00");

        assertThat(ok(get("/api/v1/cash/desk"), dispatcher, 200).at("/data/withCouriers")).hasSize(1);
        assertThat(ok(get("/api/v1/cash/desk?district=COLOMBO"), kandyDispatcher, 200).at("/data/withCouriers")).isEmpty();
        assertThat(ok(get("/api/v1/cash/desk"), admin, 200).at("/data/withCouriers/0/amount").decimalValue())
                .isEqualByComparingTo("350");
        assertThat(ok(get("/api/v1/cash/desk?district=KANDY"), admin, 200).at("/data/withCouriers")).isEmpty();
        status(send(get("/api/v1/cash/desk"), courier), 403);
        assertThat(ok(get("/internal/v1/cash/couriers/" + courier.id() + "/in-hand"), admin, 200)
                .at("/data/inHand").decimalValue()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("The first courier keeps a parcel they handed in at the hub in their day, without the next courier's part")
    void handedInParcelStaysInTheDay() throws Exception {
        DeliverySession job = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, "670.00");
        ok(step(job, courier, "PICKED_UP", "670"), 200);
        ok(step(job, courier, "AT_ORIGIN_HUB", null), 200);
        DeliverySession later = sessions.findByDeliveryId(job.getDeliveryId()).orElseThrow();
        later.setCurrentState(DeliveryState.OUT_FOR_DELIVERY);
        later.setCourierId(kandyCourier.id());
        later.setCourierName("Kandy courier");
        sessions.saveAndFlush(later);

        JsonNode list = ok(get("/api/v1/deliveries/me"), courier, 200).get("data");
        assertThat(list).hasSize(1);
        assertThat(list.at("/0/currentState").asText()).isEqualTo("AT_ORIGIN_HUB");
        assertThat(list.toString()).doesNotContain("Kandy courier");
        assertThat(ok(get("/api/v1/cash/me"), courier, 200).at("/data/inHand").decimalValue()).isEqualByComparingTo("670");
    }

    @Test
    @DisplayName("A cancelled order closes a job that was not picked up, and leaves one on the way alone")
    void cancelledOrderClosesTheJob() throws Exception {
        DeliverySession waiting = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, "350.00");
        DeliverySession moving = pickUp("350.00");

        for (DeliverySession job : new DeliverySession[] {waiting, moving, waiting}) {
            deliveryService.handleOrderCancelled(
                    new OrderCancelledEvent(null, null, job.getOrderId(), null, "Changed my mind", "CUSTOMER", null));
        }

        assertThat(state(waiting)).isEqualTo(DeliveryState.CANCELLED);
        assertThat(state(moving)).isEqualTo(DeliveryState.PICKED_UP);
    }

    @Test
    @DisplayName("The report: revenue under the pickup district less refunds, Colombo days, shortages, cash not banked")
    void report() throws Exception {
        DeliverySession damaged = pickUp("350.00");
        pickUp("420.00");
        DeliverySession old = pickUp("500.00");
        // 22 November 00:30 in Colombo is still the 21st in UTC.
        jdbc.update("UPDATE cash_entries SET recorded_at = '2026-11-21T19:00:00Z' WHERE delivery_id = ?", old.getDeliveryId());
        DeliverySession cross = job(kandyCourier, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, "670.00");
        cross.setCurrentState(DeliveryState.OUT_FOR_DELIVERY);
        sessions.saveAndFlush(cross);
        ok(step(cross, kandyCourier, "DELIVERED", null), 200);
        String id = ok(post("/api/v1/cash/handovers"), courier, 201).at("/data/handoverId").asText();
        ok(count(id, dispatcher, "1200.00", "70 missing"), 200);
        fail(damaged, "DAMAGED");
        ok(refund(dispatcher, damaged), 201);
        pickUp("350.00");

        JsonNode old22 = ok(get("/api/v1/cash/report?from=2026-11-21&to=2026-11-22"), admin, 200).get("data");
        assertThat(old22.at("/days")).hasSize(2);
        assertThat(old22.at("/days/0/revenue").decimalValue()).isEqualByComparingTo("0");
        assertThat(old22.at("/days/1/revenue").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("500"));

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo"));
        jdbc.update("DELETE FROM cash_entries WHERE delivery_id = ?", old.getDeliveryId());
        JsonNode all = ok(get("/api/v1/cash/report?from=" + today.minusDays(1) + "&to=" + today.plusDays(1)), admin, 200).get("data");
        assertMoney(all, "revenue", "770");
        assertMoney(all, "refunds", "350");
        assertMoney(all, "shortage", "70");
        assertMoney(all, "excess", "0");
        assertMoney(all, "withCouriers", "350");
        assertMoney(all, "atHub", "850");
        assertThat(all.get("parcels").asInt()).isEqualTo(3);
        assertThat(all.get("days")).hasSize(3);
        assertThat(all.at("/districts/0/district").asText()).isEqualTo("COLOMBO");
        assertThat(all.at("/districts/0/revenue").decimalValue()).isEqualByComparingTo("770");
        assertThat(all.at("/districts/1/district").asText()).isEqualTo("KANDY");
        assertThat(all.at("/districts/1/lastMileDelivered").asInt()).isEqualTo(1);
        assertThat(all.at("/districts/1/revenue").decimalValue()).isEqualByComparingTo("0");

        JsonNode kandy = ok(get("/api/v1/cash/report?from=" + today + "&to=" + today + "&district=COLOMBO"),
                kandyDispatcher, 200).get("data");
        assertMoney(kandy, "revenue", "0");
        assertMoney(kandy, "withCouriers", "0");
        assertThat(kandy.get("districts")).hasSize(1);
    }

    @Test
    @DisplayName("The report is for operations only and needs a valid period")
    void reportRules() throws Exception {
        status(send(get("/api/v1/cash/report?from=2026-11-01&to=2026-11-02"), courier), 403);
        status(send(get("/api/v1/cash/report?from=2026-11-02&to=2026-11-01"), admin), 400);
        status(send(get("/api/v1/cash/report?from=2026-01-01&to=2026-11-01"), admin), 400);
        status(send(get("/api/v1/cash/report?from=yesterday&to=2026-11-01"), admin), 400);
        status(send(get("/api/v1/cash/report"), admin), 400);
    }

    private static void assertMoney(JsonNode report, String field, String amount) {
        assertThat(report.get(field).decimalValue()).as(field).isEqualByComparingTo(amount);
    }

    // ---- steps

    private DeliverySession pickUp(String fee) throws Exception {
        DeliverySession job = job(courier, SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, fee);
        ok(step(job, courier, "PICKED_UP", fee), 200);
        return sessions.findByDeliveryId(job.getDeliveryId()).orElseThrow();
    }

    private String handOver(String fee) throws Exception {
        pickUp(fee);
        return ok(post("/api/v1/cash/handovers"), courier, 201).at("/data/handoverId").asText();
    }

    private void fail(DeliverySession job, String reason) throws Exception {
        ok(send(post("/api/v1/deliveries/{id}/transition", job.getDeliveryId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetState\":\"FAILED\",\"failureReason\":\"" + reason + "\",\"notes\":\"x\"}"), courier), 200);
    }

    private MvcResult step(DeliverySession job, Person who, String target, String cash) throws Exception {
        return send(post("/api/v1/deliveries/{id}/transition", job.getDeliveryId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetState\":\"" + target + "\",\"cashCollected\":" + cash + "}"), who);
    }

    private MvcResult count(String handoverId, Person who, String amount, String note) throws Exception {
        return send(post("/api/v1/cash/handovers/{id}/count", handoverId).contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("countedAmount", new BigDecimal(amount)).put("note", note).toString()), who);
    }

    private MvcResult deposit(Person who, String amount) throws Exception {
        return send(post("/api/v1/cash/deposits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"reference\":\"BOC-1\"}"), who);
    }

    private MvcResult refund(Person who, DeliverySession job) throws Exception {
        return send(post("/api/v1/cash/refunds").contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"" + job.getTrackingNumber().toLowerCase() + "\",\"reason\":\"Crushed\"}"), who);
    }

    // ---- fixtures

    private DeliverySession job(Person holder, SriLankanDistrict from, SriLankanDistrict to, String fee) {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setOrderId(UUID.randomUUID());
        session.setTrackingNumber("SD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        session.setCourierId(holder.id());
        session.setCourierName("Nuwan Silva");
        session.setPickupDistrict(from);
        session.setDeliveryDistrict(to);
        session.setCurrentState(DeliveryState.ASSIGNED);
        order(session, false, fee);
        return sessions.saveAndFlush(session);
    }

    private void order(DeliverySession session, boolean cancelled, String fee) {
        when(orderJobClient.get(session.getOrderId())).thenReturn(new OrderJobClient.OrderJob(session.getOrderId(), cancelled,
                OrderSpeed.STANDARD, null, null, null, null, null, null, null, null,
                new BigDecimal(fee), "LKR"));
    }

    private DeliveryState state(DeliverySession job) {
        return sessions.findByDeliveryId(job.getDeliveryId()).orElseThrow().getCurrentState();
    }

    private int entries() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cash_entries", Integer.class);
    }

    private MvcResult send(MockHttpServletRequestBuilder request, Person who) throws Exception {
        request.header(JwtHeaderConstants.X_USER_ID, who.id().toString())
                .header(JwtHeaderConstants.X_USER_ROLE, who.role().name());
        if (who.district() != null) {
            request.header(JwtHeaderConstants.X_DISTRICT_ID, who.district().name());
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, Person who, int status) throws Exception {
        return ok(send(request, who), status);
    }

    private JsonNode ok(MvcResult result, int status) throws Exception {
        status(result, status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String message(MvcResult result, int status) throws Exception {
        return ok(result, status).get("message").asText();
    }

    private static void status(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
    }
}
