package com.swiftdrop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.CourierJobStop;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** One visit to a shop: the parcels taken, one amount, one payment. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ShopStopIT {

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
    private JdbcTemplate jdbc;

    @Autowired
    private DeliverySessionRepository sessions;

    @Autowired
    private DeliveryExecutionService deliveryService;

    private final UUID shop = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final Map<UUID, OrderJobClient.OrderJob> orders = new HashMap<>();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM cash_entries");
        jdbc.update("DELETE FROM merchant_payments");
        jdbc.update("DELETE FROM delivery_sessions");
        orders.clear();
        when(orderJobClient.getAll(any())).thenAnswer(call -> {
            Map<UUID, OrderJobClient.OrderJob> found = new HashMap<>(orders);
            found.keySet().retainAll(call.<List<UUID>>getArgument(0));
            return found;
        });
        when(orderJobClient.get(any())).thenAnswer(call -> orders.get(call.<UUID>getArgument(0)));
    }

    @Test
    @DisplayName("The shop's parcels are one stop with one total; the ones left out stay for later")
    void oneStopOneTotal() throws Exception {
        DeliverySession local = parcel("350.00", SriLankanDistrict.COLOMBO);
        DeliverySession kandy = parcel("550.00", SriLankanDistrict.KANDY);
        DeliverySession notReady = parcel("550.00", SriLankanDistrict.GALLE);

        JsonNode stop = ok(get("/api/v1/deliveries/shops/" + shop), courier, 200);
        assertThat(stop.get("merchantName").asText()).isEqualTo("Nimal Fashion");
        assertThat(stop.get("addresses")).hasSize(1);
        assertThat(stop.at("/addresses/0/pickup/phone").asText()).isEqualTo("+94771234567");
        assertThat(stop.at("/addresses/0/total").decimalValue()).isEqualByComparingTo("1450");
        assertThat(stop.at("/addresses/0/parcels/1/viaHub").asBoolean()).isTrue();

        // The amount must be exactly the fees of the parcels taken.
        assertThat(message(collect(courier, "1450.00", local, kandy), 409)).contains("LKR 900.00");
        assertThat(entries()).isZero();
        JsonNode done = data(collect(courier, "900.00", local, kandy), 200);

        assertThat(done.get("parcels").asInt()).isEqualTo(2);
        assertThat(done.at("/payment/amount").decimalValue()).isEqualByComparingTo("900");
        assertThat(state(local)).isEqualTo(DeliveryState.PICKED_UP);
        assertThat(state(notReady)).isEqualTo(DeliveryState.ASSIGNED);
        assertThat(entries()).isEqualTo(2);
        assertThat(ok(get("/api/v1/cash/me"), courier, 200).get("inHand").decimalValue()).isEqualByComparingTo("900");
        assertThat(ok(get("/api/v1/deliveries/shops/" + shop), courier, 200).at("/addresses/0/parcels")).hasSize(1);
        status(collect(courier, "900.00", local, kandy), 409);
    }

    @Test
    @DisplayName("All of them or none: a cancelled parcel, or another courier's, stops the whole collection")
    void allOrNone() throws Exception {
        DeliverySession fine = parcel("350.00", SriLankanDistrict.COLOMBO);
        DeliverySession cancelled = parcel("350.00", SriLankanDistrict.COLOMBO);
        order(cancelled.getOrderId(), "350.00", true, "12 Galle Road");

        assertThat(message(collect(courier, "700.00", fine, cancelled), 409)).contains("cancelled");
        status(collect(UUID.randomUUID(), "350.00", fine), 409);
        status(send(post("/api/v1/deliveries/shops/" + shop + "/collect").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryIds\":[],\"cashCollected\":0}"), courier, UserRole.COURIER, null), 400);
        status(send(get("/api/v1/deliveries/shops/" + shop), UUID.randomUUID(), UserRole.MERCHANT, shop), 403);

        assertThat(state(fine)).isEqualTo(DeliveryState.ASSIGNED);
        assertThat(entries()).isZero();
        assertThat(ok(get("/api/v1/deliveries/shops/" + shop), courier, 200).at("/addresses/0/parcels")).hasSize(1);
    }

    @Test
    @DisplayName("A shop with two pickup addresses is shown as two, each with its own total")
    void twoAddresses() throws Exception {
        parcel("350.00", SriLankanDistrict.COLOMBO);
        DeliverySession elsewhere = parcel("400.00", SriLankanDistrict.COLOMBO);
        order(elsewhere.getOrderId(), "400.00", false, "5 Duplication Road");

        JsonNode addresses = ok(get("/api/v1/deliveries/shops/" + shop), courier, 200).get("addresses");
        assertThat(addresses).hasSize(2);
        assertThat(addresses.get(0).get("total").decimalValue()).isEqualByComparingTo("350");
        assertThat(addresses.get(1).get("total").decimalValue()).isEqualByComparingTo("400");
    }

    @Test
    @DisplayName("The shop sees its own payments and their parcels, and nobody else's")
    void shopSeesItsPayments() throws Exception {
        DeliverySession one = parcel("350.00", SriLankanDistrict.COLOMBO);
        DeliverySession two = parcel("550.00", SriLankanDistrict.KANDY);
        data(collect(courier, "900.00", one, two), 200);
        // One collected on its own from the job page is a payment of its own.
        DeliverySession alone = parcel("350.00", SriLankanDistrict.COLOMBO);
        data(send(post("/api/v1/deliveries/{id}/transition", alone.getDeliveryId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetState\":\"PICKED_UP\",\"cashCollected\":350}"), courier, UserRole.COURIER, null), 200);

        JsonNode payments = merchant(get("/api/v1/cash/payments"), shop, 200);
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).get("parcels").asInt()).isEqualTo(1);
        assertThat(payments.get(1).get("amount").decimalValue()).isEqualByComparingTo("900");
        String id = payments.get(1).get("paymentId").asText();
        assertThat(merchant(get("/api/v1/cash/payments/" + id + "/entries"), shop, 200)).hasSize(2);

        assertThat(merchant(get("/api/v1/cash/payments"), UUID.randomUUID(), 200)).isEmpty();
        status(send(get("/api/v1/cash/payments/" + id + "/entries"), UUID.randomUUID(), UserRole.MERCHANT, UUID.randomUUID()), 404);
        status(send(get("/api/v1/cash/payments"), UUID.randomUUID(), UserRole.MERCHANT, null), 403);
        status(send(get("/api/v1/cash/payments"), courier, UserRole.COURIER, null), 403);
    }

    @Test
    @DisplayName("A customer who names a shop's id on their order is not treated as the shop")
    void customerOrderIsNotTheShops() throws Exception {
        UUID orderId = UUID.randomUUID();
        order(orderId, "350.00", false, "12 Galle Road");
        deliveryService.handleCourierAssigned(assigned(orderId, SriLankanDistrict.COLOMBO, UUID.randomUUID()));

        assertThat(ok(get("/api/v1/deliveries/shops/" + shop), courier, 200).get("addresses")).isEmpty();
        assertThat(sessions.findByOrderId(orderId).orElseThrow().getMerchantId()).isNull();
    }

    private DeliverySession parcel(String fee, SriLankanDistrict to) {
        UUID orderId = UUID.randomUUID();
        order(orderId, fee, false, "12 Galle Road");
        return deliveryService.handleCourierAssigned(assigned(orderId, to, shop));
    }

    private CourierAssignedEvent assigned(UUID orderId, SriLankanDistrict to, UUID customerId) {
        return new CourierAssignedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, courier, "Nuwan Silva", 30,
                Instant.now(), Instant.now(), "SD-" + orderId.toString().substring(0, 8).toUpperCase(), shop, customerId,
                SriLankanDistrict.COLOMBO, to, "Nimal Fashion");
    }

    private void order(UUID orderId, String fee, boolean cancelled, String address) {
        orders.put(orderId, new OrderJobClient.OrderJob(orderId, cancelled, OrderSpeed.STANDARD,
                new CourierJobStop("Nimal", "+94771234567", null, address, null, "Colombo 03", "00300", SriLankanDistrict.COLOMBO),
                null, null, "Shirts", null, null, null, BigDecimal.ONE, new BigDecimal(fee), "LKR"));
    }

    private MvcResult collect(UUID who, String cash, DeliverySession... taken) throws Exception {
        return send(post("/api/v1/deliveries/shops/" + shop + "/collect").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("cashCollected", new BigDecimal(cash), "deliveryIds",
                        List.of(taken).stream().map(DeliverySession::getDeliveryId).toList()))), who, UserRole.COURIER, null);
    }

    private DeliveryState state(DeliverySession session) {
        return sessions.findByDeliveryId(session.getDeliveryId()).orElseThrow().getCurrentState();
    }

    private int entries() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cash_entries", Integer.class);
    }

    private MvcResult send(MockHttpServletRequestBuilder request, UUID who, UserRole role, UUID merchantId) throws Exception {
        request.header(JwtHeaderConstants.X_USER_ID, who.toString()).header(JwtHeaderConstants.X_USER_ROLE, role.name());
        if (role == UserRole.COURIER) {
            request.header(JwtHeaderConstants.X_DISTRICT_ID, "COLOMBO");
        }
        if (merchantId != null) {
            request.header(JwtHeaderConstants.X_MERCHANT_ID, merchantId.toString());
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, UUID who, int status) throws Exception {
        return data(send(request, who, UserRole.COURIER, null), status);
    }

    private JsonNode merchant(MockHttpServletRequestBuilder request, UUID merchantId, int status) throws Exception {
        return data(send(request, UUID.randomUUID(), UserRole.MERCHANT, merchantId), status);
    }

    private JsonNode data(MvcResult result, int status) throws Exception {
        status(result, status);
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String message(MvcResult result, int status) throws Exception {
        status(result, status);
        return json.readTree(result.getResponse().getContentAsString()).get("message").asText();
    }

    private static void status(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
    }
}
