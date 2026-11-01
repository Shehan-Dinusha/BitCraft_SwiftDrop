package com.swiftdrop.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import com.swiftdrop.order.domain.entity.OrderSource;
import com.swiftdrop.order.domain.entity.OrderStatusHistory;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.domain.repository.OrderStatusHistoryRepository;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.service.pricing.PricingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Event-flow integration test: a real PostgreSQL (Flyway migrations V1..V4 from scratch, entity validation on)
 * and a real RabbitMQ. Creating an order persists the row and its history, and publishes exactly one
 * {@code order.created} on the shared exchange. Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class OrderPersistenceAndEventIT {

    private static final String EXCHANGE = "swiftdrop.events.exchange";
    private static final String ORDER_CREATED_KEY = "order.created";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
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
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CustomerOrderRepository orderRepository;
    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @MockitoBean
    private PricingClient pricingClient;

    private String queueName;

    @BeforeEach
    void bindTestQueue() {
        when(pricingClient.estimate(any(FeeEstimateRequest.class))).thenReturn(FeeEstimateResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .speed(OrderSpeed.EXPRESS)
                .chargeableWeightKg(new BigDecimal("2.40"))
                .estimatedFee(new BigDecimal("1185.00"))
                .pricingRuleVersionId("PRICING-IT-V1")
                .currency("LKR")
                .build());

        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        rabbitAdmin.declareExchange(exchange);
        queueName = "it.order-created." + UUID.randomUUID();
        Queue queue = new Queue(queueName, false, true, true);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ORDER_CREATED_KEY));
    }

    // ---- helpers ----

    private List<JsonNode> awaitEvents(int expected) {
        List<JsonNode> events = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message message;
            while ((message = rabbitTemplate.receive(queueName)) != null) {
                events.add(objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8)));
            }
            return events.size() >= expected;
        });
        return events;
    }

    private void assertNoFurtherEvents() {
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6))
                .until(() -> rabbitTemplate.receive(queueName) == null);
    }

    private String customerOrderJson() throws Exception {
        return objectMapper.writeValueAsString(CreateOrderRequest.builder()
                .pickupAddressLine1("No. 42, Galle Road").pickupCity("Colombo 03")
                .pickupDistrict(SriLankanDistrict.COLOMBO).pickupPostalCode("00300")
                .pickupContactName("Sunil Perera").pickupContactPhone("+94771234567")
                .deliveryAddressLine1("No. 18, Peradeniya Road").deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY).deliveryPostalCode("20000")
                .recipientName("Kasun Perera").recipientPhone("0711234567").recipientEmail("kasun@example.com")
                .lengthCm(new BigDecimal("30")).widthCm(new BigDecimal("20")).heightCm(new BigDecimal("15"))
                .actualWeightKg(new BigDecimal("2.4")).speed(OrderSpeed.EXPRESS)
                .prohibitedItemsDeclared(true)
                .build());
    }

    private String merchantOrderJson(UUID merchantId, String externalRef) {
        return """
                {"merchantId":"%s","externalRef":"%s",
                 "pickupAddressLine1":"No. 42, Galle Road","pickupCity":"Colombo 03","pickupDistrict":"COLOMBO","pickupPostalCode":"00300",
                 "pickupContactName":"Sunil Perera","pickupContactPhone":"+94771234567",
                 "deliveryAddressLine1":"No. 18, Peradeniya Road","deliveryCity":"Kandy","deliveryDistrict":"KANDY","deliveryPostalCode":"20000",
                 "recipientName":"Kasun Perera","recipientPhone":"0711234567",
                 "actualWeightKg":2.4,"speed":"EXPRESS","prohibitedItemsDeclared":true}
                """.formatted(merchantId, externalRef);
    }

    // ---- tests ----

    @Test
    @DisplayName("POST /api/v1/orders persists the order and its history, and publishes exactly one order.created with the caller's correlation id")
    void customerOrderIsPersistedAndPublished() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", customerId.toString())
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerOrderJson()))
                .andReturn().getResponse().getContentAsString();
        String trackingNumber = objectMapper.readTree(response).at("/data/trackingNumber").asText();
        assertThat(trackingNumber).matches("SD-[A-Z0-9]{4}-[A-Z0-9]{4}");

        CustomerOrder saved = orderRepository.findByTrackingNumber(trackingNumber).orElseThrow();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getSource()).isEqualTo(OrderSource.CUSTOMER);
        assertThat(saved.getStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(saved.getEstimatedFee()).isEqualByComparingTo("1185.00");
        assertThat(saved.getPricingRuleVersionId()).isEqualTo("PRICING-IT-V1");

        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByOccurredAtAscIdAsc(saved.getOrderId());
        assertThat(history).extracting(OrderStatusHistory::getStatus).containsExactly(PublicOrderStatus.CREATED);

        List<JsonNode> events = awaitEvents(1);
        assertThat(events).hasSize(1);
        JsonNode event = events.get(0);
        assertThat(event.get("trackingNumber").asText()).isEqualTo(trackingNumber);
        assertThat(event.get("orderId").asText()).isEqualTo(saved.getOrderId().toString());
        assertThat(event.get("customerId").asText()).isEqualTo(customerId.toString());
        assertThat(event.get("correlationId").asText()).isEqualTo(correlationId.toString());
        assertThat(event.toString()).as("no personal data in the event").doesNotContain("Galle Road", "Perera", "0711234567", "kasun@example.com");
        assertNoFurtherEvents();
    }

    @Test
    @DisplayName("POST /internal/v1/orders persists a MERCHANT order and publishes the same order.created; a repeat publishes nothing")
    void merchantOrderIsPersistedPublishedAndIdempotent() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String ref = "IT-REF-" + UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String first = mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantOrderJson(merchantId, ref)))
                .andReturn().getResponse().getContentAsString();
        String trackingNumber = objectMapper.readTree(first).at("/data/trackingNumber").asText();

        CustomerOrder saved = orderRepository.findByMerchantIdAndExternalRef(merchantId, ref).orElseThrow();
        assertThat(saved.getTrackingNumber()).isEqualTo(trackingNumber);
        assertThat(saved.getSource()).isEqualTo(OrderSource.MERCHANT);
        assertThat(saved.getCustomerId()).as("merchant id is stored as the customer id").isEqualTo(merchantId);
        assertThat(saved.getRecipientEmail()).isNull();

        List<JsonNode> events = awaitEvents(1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("trackingNumber").asText()).isEqualTo(trackingNumber);
        assertThat(events.get(0).get("merchantId").asText()).isEqualTo(merchantId.toString());
        assertThat(events.get(0).get("customerId").asText()).isEqualTo(merchantId.toString());
        assertThat(events.get(0).get("correlationId").asText()).isEqualTo(correlationId.toString());
        assertNoFurtherEvents();

        String repeat = mockMvc.perform(post("/internal/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantOrderJson(merchantId, ref)))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(repeat).at("/data/trackingNumber").asText()).isEqualTo(trackingNumber);
        assertThat(orderRepository.findAll().stream()
                .filter(o -> merchantId.equals(o.getMerchantId())).count()).isEqualTo(1);
        assertNoFurtherEvents();
    }

    @Test
    @DisplayName("Concurrent identical merchant requests create one order, answer 201 once and 200 for the rest, and publish one event")
    void concurrentMerchantRequestsCreateOneOrder() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String ref = "IT-RACE-" + UUID.randomUUID();
        String body = merchantOrderJson(merchantId, ref);
        int callers = 6;

        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Integer> statuses = new CopyOnWriteArrayList<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                Callable<Void> call = () -> {
                    ready.countDown();
                    go.await();
                    statuses.add(mockMvc.perform(post("/internal/v1/orders")
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus());
                    return null;
                };
                futures.add(pool.submit(call));
            }
            ready.await();
            go.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).hasSize(callers);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(callers - 1);
        assertThat(orderRepository.findAll().stream().filter(o -> merchantId.equals(o.getMerchantId())).count()).isEqualTo(1);
        assertThat(awaitEvents(1)).hasSize(1);
        assertNoFurtherEvents();
    }

    // ---- status sync (Stage 12) ----

    private CustomerOrder createCustomerOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerOrderJson()))
                .andReturn().getResponse().getContentAsString();
        String trackingNumber = objectMapper.readTree(response).at("/data/trackingNumber").asText();
        return orderRepository.findByTrackingNumber(trackingNumber).orElseThrow();
    }

    private ParcelStatusChangedEvent statusEvent(CustomerOrder order, PublicOrderStatus from, PublicOrderStatus to,
                                                 InternalQueueStatus sub, Instant at) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), order.getTrackingNumber(), order.getOrderId(),
                order.getCustomerId(), from, to, sub, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, at);
    }

    private void awaitStatus(UUID orderId, PublicOrderStatus expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orderRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(expected));
    }

    private List<PublicOrderStatus> historyOf(UUID orderId) {
        return historyRepository.findByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
                .map(OrderStatusHistory::getStatus).toList();
    }

    @Test
    @DisplayName("The order service's own parcel.status.changed event on creation is ignored: no loop, no duplicate history")
    void ownCreationEventDoesNotLoop() throws Exception {
        CustomerOrder order = createCustomerOrder();

        // Give the consumer time to receive the order's own event, then check nothing changed.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertThat(orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(PublicOrderStatus.CREATED);
            assertThat(historyOf(order.getOrderId())).containsExactly(PublicOrderStatus.CREATED);
        });
    }

    @Test
    @DisplayName("Delivery status events move the order along, on both the bare and the dotted routing key")
    void deliveryEventsUpdateTheOrder() throws Exception {
        CustomerOrder order = createCustomerOrder();
        Instant base = Instant.now().plusSeconds(60);

        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed",
                statusEvent(order, PublicOrderStatus.CREATED, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND, base));
        awaitStatus(order.getOrderId(), PublicOrderStatus.IN_TRANSIT);
        assertThat(orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getInternalQueueStatus())
                .isEqualTo(InternalQueueStatus.OUTBOUND);

        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed.out_for_delivery",
                statusEvent(order, PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.OUT_FOR_DELIVERY, InternalQueueStatus.OUT_FOR_DELIVERY, base.plusSeconds(60)));
        awaitStatus(order.getOrderId(), PublicOrderStatus.OUT_FOR_DELIVERY);

        assertThat(historyOf(order.getOrderId())).containsExactly(
                PublicOrderStatus.CREATED, PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @DisplayName("A duplicate event, an older event and an event for an unknown order change nothing and do not block later events")
    void duplicatesOutOfOrderAndUnknownOrdersAreHarmless() throws Exception {
        CustomerOrder order = createCustomerOrder();
        Instant base = Instant.now().plusSeconds(60);
        ParcelStatusChangedEvent inTransit =
                statusEvent(order, PublicOrderStatus.CREATED, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND, base.plusSeconds(60));

        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed", inTransit);
        awaitStatus(order.getOrderId(), PublicOrderStatus.IN_TRANSIT);

        // same event id delivered again
        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed", inTransit);
        // an older event arriving late
        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed",
                statusEvent(order, PublicOrderStatus.CREATED, PublicOrderStatus.PICKED_UP, InternalQueueStatus.PICKED_UP, base));
        // an event for an order this service does not have
        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed", new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SD-ZZZZ-ZZZZ", UUID.randomUUID(), UUID.randomUUID(),
                PublicOrderStatus.CREATED, PublicOrderStatus.IN_TRANSIT, null, null, null, null, base.plusSeconds(120)));
        // a later valid event must still be processed after the harmless ones
        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed",
                statusEvent(order, PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.DELIVERED, InternalQueueStatus.DELIVERED, base.plusSeconds(180)));
        awaitStatus(order.getOrderId(), PublicOrderStatus.DELIVERED);

        assertThat(historyOf(order.getOrderId())).containsExactly(
                PublicOrderStatus.CREATED, PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.DELIVERED);

        // a delivered order does not move again
        rabbitTemplate.convertAndSend(EXCHANGE, "parcel.status.changed",
                statusEvent(order, PublicOrderStatus.DELIVERED, PublicOrderStatus.IN_TRANSIT, null, base.plusSeconds(240)));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThat(orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(PublicOrderStatus.DELIVERED));
    }
}
