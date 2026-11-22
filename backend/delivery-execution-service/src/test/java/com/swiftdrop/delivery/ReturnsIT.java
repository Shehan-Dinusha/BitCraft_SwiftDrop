package com.swiftdrop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static com.swiftdrop.contracts.enums.SriLankanDistrict.COLOMBO;
import static com.swiftdrop.contracts.enums.SriLankanDistrict.KANDY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A failed delivery, from the recipient's door back to the sender's hand, over HTTP and a real PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ReturnsIT {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private DeliverySessionRepository sessions;

    @Autowired
    private DeliveryExecutionService deliveries;

    @Autowired
    private ProofOfDeliveryStorageService proofs;

    private final Person colombo = new Person(UUID.randomUUID(), UserRole.COURIER, COLOMBO);
    private final Person kandy = new Person(UUID.randomUUID(), UserRole.COURIER, KANDY);
    private final Person colomboHub = new Person(UUID.randomUUID(), UserRole.DISPATCHER, COLOMBO);
    private final Person kandyHub = new Person(UUID.randomUUID(), UserRole.DISPATCHER, KANDY);

    @BeforeEach
    void clean() {
        sessions.deleteAll();
    }

    @Test
    @DisplayName("Between districts: two attempts, then back through the sender's hub to the sender, with a new photo")
    void crossDistrictReturn() throws Exception {
        UUID id = job(kandy, COLOMBO, KANDY, DeliveryState.OUT_FOR_DELIVERY);
        proof(id);

        // An older app says FAILED; a reason that is not a loss means the attempt failed.
        go(id, kandy, "FAILED", "RECIPIENT_UNAVAILABLE");
        is(id, DeliveryState.ATTEMPT_FAILED);
        assertThat(session(id).getAttemptCount()).isEqualTo(1);
        refused(id, colomboHub, "HELD_AT_HUB", null, 403);
        go(id, kandy, "HELD_AT_HUB", null);
        assertThat(session(id).getCourierId()).isNull();

        // Only the hub that holds it decides; a step of the other route is refused.
        refused(id, colomboHub, "AT_DESTINATION_HUB", null, 403);
        refused(id, kandyHub, "RETURN_AT_HUB", null, 409);
        go(id, kandyHub, "AT_DESTINATION_HUB", null);
        assign(id, kandy);
        go(id, kandy, "OUT_FOR_DELIVERY", null);

        // The photo of the first attempt proves nothing now.
        assertThat(refused(id, kandy, "DELIVERED", null, 409)).contains("Proof of delivery");
        go(id, kandy, "ATTEMPT_FAILED", "WRONG_ADDRESS");
        go(id, kandyHub, "HELD_AT_HUB", null);
        assertThat(refused(id, kandyHub, "AT_DESTINATION_HUB", null, 409)).contains("tried 2 times");

        go(id, kandyHub, "RETURN_IN_TRANSIT", null);
        refused(id, kandyHub, "RETURN_AT_HUB", null, 403);
        go(id, colomboHub, "RETURN_AT_HUB", null);
        refused(id, colomboHub, "OUT_FOR_RETURN", null, 409);
        assign(id, colombo);
        is(id, DeliveryState.RETURN_AT_HUB);
        go(id, colombo, "OUT_FOR_RETURN", null);
        assertThat(refused(id, colombo, "FAILED", "CUSTOMER_UNAVAILABLE", 409)).contains("back to the hub");

        // The sender is not home: back to the hub, and out again another time.
        go(id, colombo, "RETURN_AT_HUB", null);
        assertThat(session(id).getCourierId()).isNull();
        assign(id, colombo);
        go(id, colombo, "OUT_FOR_RETURN", null);
        assertThat(refused(id, colombo, "RETURNED", null, 409)).contains("photo");
        proof(id);
        go(id, colombo, "RETURNED", null);

        is(id, DeliveryState.RETURNED);
        assertThat(session(id).getReturnedAt()).isNotNull();
        refused(id, colombo, "RETURN_AT_HUB", null, 409);
        assertThat(ok(get("/api/v1/deliveries/me"), colombo)).hasSize(1);
    }

    @Test
    @DisplayName("Refused: no second attempt. In one district it goes from the same hub to the sender")
    void refusedInOneDistrict() throws Exception {
        UUID id = job(colombo, COLOMBO, COLOMBO, DeliveryState.IN_TRANSIT);

        assertThat(refused(id, colombo, "ATTEMPT_FAILED", null, 409)).contains("Say why");
        go(id, colombo, "ATTEMPT_FAILED", "RECIPIENT_REJECTED");
        go(id, colomboHub, "HELD_AT_HUB", null);
        assertThat(refused(id, colomboHub, "AT_DESTINATION_HUB", null, 409)).contains("refused");
        refused(id, colomboHub, "RETURN_IN_TRANSIT", null, 409);
        go(id, colomboHub, "RETURN_AT_HUB", null);
        is(id, DeliveryState.RETURN_AT_HUB);
    }

    @Test
    @DisplayName("One district: the second attempt goes out from the hub and is delivered with its own photo")
    void secondAttemptDelivered() throws Exception {
        UUID id = job(colombo, COLOMBO, COLOMBO, DeliveryState.OUT_FOR_DELIVERY);
        go(id, colombo, "ATTEMPT_FAILED", "CUSTOMER_UNAVAILABLE");
        go(id, colombo, "HELD_AT_HUB", null);
        go(id, colomboHub, "AT_DESTINATION_HUB", null);
        assign(id, colombo);
        go(id, colombo, "OUT_FOR_DELIVERY", null);
        proof(id);
        go(id, colombo, "DELIVERED", null);
        is(id, DeliveryState.DELIVERED);
    }

    @Test
    @DisplayName("Lost or damaged ends it anywhere; another reason does not, in the middle of the route")
    void lostAndOtherReasons() throws Exception {
        UUID held = job(null, COLOMBO, KANDY, DeliveryState.HELD_AT_HUB);
        assertThat(refused(held, kandyHub, "FAILED", "OTHER", 409)).contains("lost or damaged");
        go(held, kandyHub, "FAILED", "LOST");
        is(held, DeliveryState.FAILED);

        UUID toHub = job(colombo, COLOMBO, KANDY, DeliveryState.PICKED_UP);
        refused(toHub, colombo, "FAILED", "OTHER", 409);
        go(toHub, colombo, "FAILED", "DAMAGED_IN_TRANSIT");
        is(toHub, DeliveryState.FAILED);

        UUID pickup = job(colombo, COLOMBO, COLOMBO, DeliveryState.ASSIGNED);
        go(pickup, colombo, "ATTEMPT_FAILED", "CUSTOMER_UNAVAILABLE");
        is(pickup, DeliveryState.FAILED);
    }

    @Test
    @DisplayName("A late courier assignment changes nothing on a parcel already on its way")
    void lateAssignment() throws Exception {
        UUID id = job(colombo, COLOMBO, COLOMBO, DeliveryState.IN_TRANSIT);
        assign(id, kandy);
        is(id, DeliveryState.IN_TRANSIT);
        assertThat(session(id).getCourierId()).isEqualTo(colombo.id());
    }

    // ---- steps

    private void go(UUID id, Person who, String target, String reason) throws Exception {
        MvcResult result = step(id, who, target, reason);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
    }

    private String refused(UUID id, Person who, String target, String reason, int status) throws Exception {
        MvcResult result = step(id, who, target, reason);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString()).get("message").asText();
    }

    private MvcResult step(UUID id, Person who, String target, String reason) throws Exception {
        return mockMvc.perform(as(post("/api/v1/deliveries/{id}/transition", id), who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("targetState", target).put("failureReason", reason).toString()))
                .andReturn();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, Person who) throws Exception {
        MvcResult result = mockMvc.perform(as(request, who)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private void assign(UUID id, Person courier) {
        deliveries.handleCourierAssigned(new CourierAssignedEvent(UUID.randomUUID(), UUID.randomUUID(),
                session(id).getOrderId(), courier.id(), "Courier", 25, Instant.now(), Instant.now(),
                session(id).getTrackingNumber(), null, UUID.randomUUID(),
                session(id).getPickupDistrict(), session(id).getDeliveryDistrict()));
    }

    private void proof(UUID id) {
        proofs.store(id, new ProofOfDeliveryUploadRequest("https://example.test/p.jpg", "signed"));
    }

    private UUID job(Person holder, SriLankanDistrict from, SriLankanDistrict to, DeliveryState state) {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setOrderId(UUID.randomUUID());
        session.setTrackingNumber("SD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        session.setCourierId(holder == null ? null : holder.id());
        session.setPickupDistrict(from);
        session.setDeliveryDistrict(to);
        session.setCurrentState(state);
        return sessions.saveAndFlush(session).getDeliveryId();
    }

    private DeliverySession session(UUID id) {
        return sessions.findByDeliveryId(id).orElseThrow();
    }

    private void is(UUID id, DeliveryState state) {
        assertThat(session(id).getCurrentState()).isEqualTo(state);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Person who) {
        return request.header(JwtHeaderConstants.X_USER_ID, who.id().toString())
                .header(JwtHeaderConstants.X_USER_ROLE, who.role().name())
                .header(JwtHeaderConstants.X_DISTRICT_ID, who.district().name());
    }
}
