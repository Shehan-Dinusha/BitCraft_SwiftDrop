package com.swiftdrop.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.service.CourierMatchingEngine;
import com.swiftdrop.dispatch.support.TestCallers;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A shop's parcels from booking to one courier, over HTTP and a real PostgreSQL. */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ShopPickupIT {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CourierMatchingEngine engine;

    @Autowired
    private DispatchAssignmentRepository assignments;

    @Autowired
    private CourierShiftRepository shifts;

    private final UUID shop = UUID.randomUUID();

    @BeforeEach
    void clean() {
        assignments.deleteAll();
        shifts.deleteAll();
    }

    @Test
    @DisplayName("No courier is sent until the shop says it is packed; then all its parcels go to one courier")
    void packedThenAssignedTogether() throws Exception {
        List<UUID> orders = book(3, OrderSpeed.STANDARD);
        UUID van = courier(VehicleType.VAN, 25, SriLankanDistrict.COLOMBO);

        assertThat(states(get(internal(""))).values()).containsOnly("WAITING");
        JsonNode queue = ok(get("/api/v1/dispatch/unassigned"), TestCallers.dispatcher());
        assertThat(queue).allSatisfy(row -> {
            assertThat(row.get("shopPickup").asBoolean()).isTrue();
            assertThat(row.get("readyForPickup").asBoolean()).isFalse();
        });
        status(assign(van, orders, TestCallers.dispatcher()), 409);

        assertThat(states(ready()).values()).containsOnly("READY");
        assertThat(ok(get("/api/v1/dispatch/unassigned"), TestCallers.dispatcher()).get(0).get("merchantName").asText())
                .isEqualTo("Nimal Fashion");
        status(assign(van, orders, TestCallers.dispatcherOf("KANDY")), 403);
        status(assign(van, orders, TestCallers.as(UserRole.MERCHANT)), 403);
        status(assign(van, orders, TestCallers.dispatcher()), 200);

        assertThat(states(get(internal(""))).values()).containsOnly("ASSIGNED");
        assertThat(shifts.findByCourierId(van).orElseThrow().getActiveParcelsCount()).isEqualTo(3);
        status(assign(van, orders, TestCallers.dispatcher()), 409);
    }

    @Test
    @DisplayName("A parcel booked after Ready waits for the next Ready; pressing it again keeps the first time")
    void laterParcelsWait() throws Exception {
        book(1, OrderSpeed.STANDARD);
        states(ready());
        var firstReadyAt = assignments.findAll().getFirst().getReadyAt();
        UUID later = book(1, OrderSpeed.STANDARD).getFirst();

        assertThat(states(get(internal(""))).get(later)).isEqualTo("WAITING");
        assertThat(states(ready()).get(later)).isEqualTo("READY");
        assertThat(assignments.findAll()).filteredOn(a -> !a.getOrderId().equals(later))
                .singleElement().satisfies(a -> assertThat(a.getReadyAt()).isEqualTo(firstReadyAt));
    }

    @Test
    @DisplayName("Couriers are offered with the parcels that fit together, and a load too big is refused whole")
    void fitsTogether() throws Exception {
        List<UUID> orders = book(6, OrderSpeed.STANDARD);
        states(ready());
        UUID bike = courier(VehicleType.MOTORCYCLE, 5, SriLankanDistrict.COLOMBO);
        UUID van = courier(VehicleType.VAN, 25, SriLankanDistrict.COLOMBO);
        courier(VehicleType.LORRY, 50, SriLankanDistrict.KANDY);

        JsonNode couriers = ok(get("/api/v1/dispatch/shops/{id}/couriers", shop), TestCallers.dispatcher());
        assertThat(couriers).hasSize(2);
        assertThat(couriers.get(0).get("courierId").asText()).isEqualTo(van.toString());
        assertThat(couriers.get(0).get("fittingOrderIds")).hasSize(6);
        assertThat(couriers.get(1).get("fittingOrderIds")).hasSize(5);

        status(assign(bike, orders, TestCallers.dispatcher()), 409);
        assertThat(assignments.findAll()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED));
        assertThat(shifts.findByCourierId(bike).orElseThrow().getActiveParcelsCount()).isZero();

        // Splitting it works: five on the bike, one in the van.
        status(assign(bike, orders.subList(0, 5), TestCallers.dispatcher()), 200);
        status(assign(van, orders.subList(5, 6), TestCallers.dispatcher()), 200);
    }

    @Test
    @DisplayName("An express order is assigned on its own without Ready, and a customer's order never joins a shop")
    void expressAndCustomerOrders() throws Exception {
        UUID express = book(1, OrderSpeed.EXPRESS).getFirst();
        UUID van = courier(VehicleType.VAN, 25, SriLankanDistrict.COLOMBO);
        // A customer who names the shop's id on their own order.
        UUID forged = order(UUID.randomUUID(), OrderSpeed.STANDARD);

        JsonNode queue = ok(get("/api/v1/dispatch/unassigned"), TestCallers.dispatcher());
        assertThat(queue).allSatisfy(row -> {
            assertThat(row.get("shopPickup").asBoolean()).isFalse();
            assertThat(row.get("readyForPickup").asBoolean()).isTrue();
        });
        assertThat(states(get(internal(""))).keySet()).containsExactly(express);
        status(assign(van, List.of(express), TestCallers.dispatcher()), 409);
        for (UUID order : List.of(express, forged)) {
            status(send(post("/api/v1/dispatch/assign").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("orderId", order, "courierId", van))), TestCallers.dispatcher()), 200);
        }
    }

    private List<UUID> book(int count, OrderSpeed speed) {
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(order(shop, speed));
        }
        return orders;
    }

    private UUID order(UUID customerId, OrderSpeed speed) {
        UUID orderId = UUID.randomUUID();
        engine.processOrderCreated(new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId,
                "SD-" + orderId.toString().substring(0, 8).toUpperCase(), customerId, shop, SriLankanDistrict.COLOMBO,
                SriLankanDistrict.COLOMBO, speed, null, null, "LKR", null,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ONE));
        return orderId;
    }

    private UUID courier(VehicleType vehicle, int capacity, SriLankanDistrict district) {
        CourierShift shift = new CourierShift();
        shift.setCourierId(UUID.randomUUID());
        shift.setCourierName("Courier " + vehicle);
        shift.setVehicleType(vehicle);
        shift.setMaxCapacity(capacity);
        shift.setCurrentDistrict(district);
        shift.setShiftStatus(ShiftStatus.AVAILABLE);
        return shifts.saveAndFlush(shift).getCourierId();
    }

    private String internal(String path) {
        return "/internal/v1/merchant-pickups/" + shop + path;
    }

    private MockHttpServletRequestBuilder ready() {
        return post(internal("/ready")).contentType(MediaType.APPLICATION_JSON).content("{\"merchantName\":\"Nimal Fashion\"}");
    }

    private Map<UUID, String> states(MockHttpServletRequestBuilder request) throws Exception {
        Map<UUID, String> states = new java.util.LinkedHashMap<>();
        ok(request, r -> r).forEach(row -> states.put(UUID.fromString(row.get("orderId").asText()), row.get("state").asText()));
        return states;
    }

    private MvcResult assign(UUID courier, List<UUID> orders, RequestPostProcessor caller) throws Exception {
        return send(post("/api/v1/dispatch/shops/{id}/assign", shop).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("courierId", courier, "orderIds", orders))), caller);
    }

    private MvcResult send(MockHttpServletRequestBuilder request, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(request.with(caller)).andReturn();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, RequestPostProcessor caller) throws Exception {
        MvcResult result = send(request, caller);
        status(result, 200);
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static void status(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
    }
}
