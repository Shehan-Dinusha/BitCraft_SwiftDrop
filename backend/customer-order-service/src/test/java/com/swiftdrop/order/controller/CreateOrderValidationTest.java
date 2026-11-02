package com.swiftdrop.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.UpdateOrderRequest;
import com.swiftdrop.order.service.CustomerOrderService;
import org.junit.jupiter.api.BeforeEach;
import com.swiftdrop.order.exception.CallerIdentityRequiredException;
import com.swiftdrop.order.exception.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(OrderController.class)
class CreateOrderValidationTest {

    private static final String SQLI = "' OR 1=1 --";
    private static final String XSS = "<script>alert(1)</script>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerOrderService customerOrderService;

    @BeforeEach
    void stubService() {
        when(customerOrderService.createOrder(any(), any(), any())).thenReturn(
                CustomerOrderResponse.builder().orderId(UUID.randomUUID()).trackingNumber("SD-7K4M-9QX2").build());
        when(customerOrderService.updateOrder(any(), any(), any(), any())).thenReturn(
                CustomerOrderResponse.builder().orderId(UUID.randomUUID()).trackingNumber("SD-7K4M-9QX2").build());
    }

    private CreateOrderRequest validRequest() {
        return CreateOrderRequest.builder()
                .pickupAddressLine1("No. 42, Galle Road")
                .pickupAddressLine2("Suite 4B")
                .pickupCity("Colombo 03")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00300")
                .pickupContactName("Sunil Perera")
                .pickupContactPhone("+94771234567")
                .deliveryAddressLine1("No. 18, Peradeniya Road")
                .deliveryAddressLine2("Hilltop Residence")
                .deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20000")
                .recipientName("Kasun O'Neil-Perera")
                .recipientPhone("0711234567")
                .recipientEmail("kasun@example.com")
                .lengthCm(new BigDecimal("30"))
                .widthCm(new BigDecimal("20"))
                .heightCm(new BigDecimal("15"))
                .actualWeightKg(new BigDecimal("2.4"))
                .speed(OrderSpeed.EXPRESS)
                .itemDescription("Batik sarees (2)")
                .prohibitedItemsDeclared(true)
                .build();
    }

    private ResultActions create(Consumer<CreateOrderRequest> mutation) throws Exception {
        CreateOrderRequest request = validRequest();
        mutation.accept(request);
        return mockMvc.perform(post("/api/v1/orders")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("A fully valid request is accepted")
    void validRequestSucceeds() throws Exception {
        create(r -> { }).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Optional address line 2 may be empty or missing")
    void optionalLineTwoMayBeEmpty() throws Exception {
        create(r -> r.setPickupAddressLine2("")).andExpect(status().isOk());
        create(r -> r.setDeliveryAddressLine2(null)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] unsafe address text is rejected")
    @ValueSource(strings = {SQLI, XSS, "No. 5 <b>Main</b> Street", "Road; DROP TABLE customer_orders"})
    void unsafeAddressTextIsRejected(String unsafe) throws Exception {
        create(r -> r.setPickupAddressLine1(unsafe)).andExpect(status().isBadRequest());
        create(r -> r.setPickupAddressLine2(unsafe)).andExpect(status().isBadRequest());
        create(r -> r.setDeliveryAddressLine1(unsafe)).andExpect(status().isBadRequest());
        create(r -> r.setDeliveryAddressLine2(unsafe)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "[{index}] unsafe city and name text is rejected")
    @ValueSource(strings = {SQLI, XSS})
    void unsafeCityAndNameTextIsRejected(String unsafe) throws Exception {
        create(r -> r.setPickupCity(unsafe)).andExpect(status().isBadRequest());
        create(r -> r.setDeliveryCity(unsafe)).andExpect(status().isBadRequest());
        create(r -> r.setPickupContactName(unsafe)).andExpect(status().isBadRequest());
        create(r -> r.setRecipientName(unsafe)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Script tags in the item description are rejected")
    void scriptInDescriptionIsRejected() throws Exception {
        create(r -> r.setItemDescription(XSS)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Address lines shorter than 5 or longer than 200 characters are rejected")
    void addressLengthIsEnforced() throws Exception {
        create(r -> r.setPickupAddressLine1("abcd")).andExpect(status().isBadRequest());
        create(r -> r.setDeliveryAddressLine1("a".repeat(201))).andExpect(status().isBadRequest());
        create(r -> r.setPickupAddressLine2("a".repeat(201))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "[{index}] invalid phone is rejected")
    @ValueSource(strings = {"12345", "0771234", "07712345678", "+9477123456789", "+94 77 123 4567", "abcdefghij", "077-123-4567"})
    void invalidPhoneIsRejected(String phone) throws Exception {
        create(r -> r.setPickupContactPhone(phone)).andExpect(status().isBadRequest());
        create(r -> r.setRecipientPhone(phone)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "[{index}] valid phone is accepted")
    @ValueSource(strings = {"0771234567", "+94771234567", "0112345678"})
    void validPhoneIsAccepted(String phone) throws Exception {
        create(r -> r.setPickupContactPhone(phone)).andExpect(status().isOk());
        create(r -> r.setRecipientPhone(phone)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] invalid postal code is rejected")
    @ValueSource(strings = {"1234", "123456", "abcde", "12 45"})
    void invalidPostalCodeIsRejected(String postalCode) throws Exception {
        create(r -> r.setPickupPostalCode(postalCode)).andExpect(status().isBadRequest());
        create(r -> r.setDeliveryPostalCode(postalCode)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "[{index}] out-of-range weight is rejected")
    @ValueSource(strings = {"0", "0.009", "-1", "50.01", "51"})
    void weightOutOfRangeIsRejected(String weight) throws Exception {
        create(r -> r.setActualWeightKg(new BigDecimal(weight))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "[{index}] weight within range is accepted")
    @ValueSource(strings = {"0.01", "1.5", "50"})
    void weightWithinRangeIsAccepted(String weight) throws Exception {
        create(r -> r.setActualWeightKg(new BigDecimal(weight))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Zero or negative dimensions are rejected")
    void nonPositiveDimensionsAreRejected() throws Exception {
        create(r -> r.setLengthCm(BigDecimal.ZERO)).andExpect(status().isBadRequest());
        create(r -> r.setWidthCm(new BigDecimal("-5"))).andExpect(status().isBadRequest());
        create(r -> r.setHeightCm(BigDecimal.ZERO)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Oversized recipient email is rejected")
    void oversizedEmailIsRejected() throws Exception {
        create(r -> r.setRecipientEmail("a".repeat(125) + "@x.lk")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Client-supplied pricing rule version must be a plain identifier")
    void pricingRuleVersionIsRestricted() throws Exception {
        create(r -> r.setPricingRuleVersionId("PRICING-2026-V1")).andExpect(status().isOk());
        create(r -> r.setPricingRuleVersionId(XSS)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fee estimate rejects a weight above 50 kg")
    void estimateRejectsOverweight() throws Exception {
        FeeEstimateRequest request = FeeEstimateRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("51"))
                .build();

        mockMvc.perform(post("/api/v1/orders/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- Stage 5: error bodies and logs must not echo input ----

    private static final String SECRET_ADDRESS = "77 Hidden Lane <b>secret</b>";
    private static final String SECRET_PHONE = "0719998887x";
    private static final String SECRET_NAME = "Zed<script>Quux</script>";

    @Test
    @DisplayName("Validation error body names the fields but contains no submitted text")
    void validationErrorBodyDoesNotEchoInput(CapturedOutput output) throws Exception {
        String body = create(r -> {
            r.setPickupAddressLine1(SECRET_ADDRESS);
            r.setDeliveryAddressLine1(SQLI);
            r.setPickupContactPhone(SECRET_PHONE);
            r.setRecipientName(SECRET_NAME);
            r.setItemDescription(XSS);
        }).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("pickupAddressLine1", "deliveryAddressLine1", "pickupContactPhone", "recipientName", "itemDescription")
                .doesNotContain("Hidden Lane", "secret", SECRET_PHONE, "0719998887", "Quux", "<script", "OR 1=1", "alert");
        assertThat(output.getAll())
                .contains("Validation error on")
                .doesNotContain("Hidden Lane", "0719998887", "Quux", "OR 1=1", "alert(1)");
    }

    @Test
    @DisplayName("Malformed JSON or an invalid enum value gives a generic 400 without the offending text")
    void unreadableBodyDoesNotEchoInput(CapturedOutput output) throws Exception {
        String badEnum = "{\"pickupDistrict\":\"ATLANTIS-secret-42\"}";
        String malformed = "{\"pickupAddressLine1\": \"Leaky Street 99\" oops";

        for (String content : new String[] {badEnum, malformed}) {
            String body = mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("ATLANTIS", "secret-42", "Leaky", "oops");
        }
        assertThat(output.getAll()).doesNotContain("ATLANTIS", "secret-42", "Leaky Street");
    }

    @Test
    @DisplayName("A malformed id in the path gives a generic 400 without echoing the id or the raw URI")
    void invalidPathValueDoesNotEchoInput() throws Exception {
        String body = mockMvc.perform(get("/api/v1/orders/{orderId}", "not-a-uuid-leaky-123"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("leaky-123", "not-a-uuid").contains("orderId");
    }

    @Test
    @DisplayName("Not-found response never contains the requested tracking number")
    void notFoundDoesNotEchoTrackingNumber() throws Exception {
        when(customerOrderService.getOrderByTrackingNumber("SD-ZZZZ-9999"))
                .thenThrow(new OrderNotFoundException("Order not found with tracking number: SD-ZZZZ-9999"));

        String body = mockMvc.perform(get("/api/v1/orders/tracking/{trackingNumber}", "SD-ZZZZ-9999"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("ZZZZ").contains("Order not found");
    }

    @Test
    @DisplayName("Unexpected errors return a fixed 500 message; the log keeps the stack trace but not exception messages")
    void internalErrorHidesExceptionText(CapturedOutput output) throws Exception {
        IllegalStateException cause = new IllegalStateException("root cause leaks Nimal 0716667778");
        when(customerOrderService.createOrder(any(), any(), any()))
                .thenThrow(new RuntimeException("could not execute statement: insert into customer_orders ... Key (recipient_phone)=(0714445556)", cause));

        String body = create(r -> { }).andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("insert into", "customer_orders", "0714445556", "recipient_phone")
                .contains("An unexpected error occurred");
        assertThat(output.getAll())
                .doesNotContain("insert into", "0714445556", "0716667778", "Nimal")
                .contains("Internal error on /api/v1/orders")
                .contains("java.lang.RuntimeException")
                .contains("Caused by: java.lang.IllegalStateException")
                .contains("\tat com.swiftdrop.order.controller.CreateOrderValidationTest");
    }

    @Test
    @DisplayName("Missing caller identity returns 401 with a specific message")
    void missingCallerIdentityReturns401() throws Exception {
        when(customerOrderService.listCustomerOrders(any(), any(), any(), any()))
                .thenThrow(new CallerIdentityRequiredException("Customer identification is required"));
        when(customerOrderService.createOrder(any(), any(), any()))
                .thenThrow(new CallerIdentityRequiredException("Customer identification is required"));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Customer identification is required"));
        create(r -> { }).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Illegal-argument errors return a fixed message, not the exception text")
    void illegalArgumentHidesExceptionText() throws Exception {
        when(customerOrderService.createOrder(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad value 0713334445 for Nimal"));

        String body = create(r -> { }).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("0713334445", "Nimal");
    }

    @Test
    @DisplayName("Update validates the fields it carries")
    void updateValidatesProvidedFields() throws Exception {
        UUID orderId = UUID.randomUUID();
        UpdateOrderRequest unsafe = UpdateOrderRequest.builder().pickupAddressLine1(XSS).build();
        UpdateOrderRequest overweight = UpdateOrderRequest.builder().actualWeightKg(new BigDecimal("51")).build();
        UpdateOrderRequest badPhone = UpdateOrderRequest.builder().recipientPhone("12345").build();
        UpdateOrderRequest fine = UpdateOrderRequest.builder().pickupAddressLine1("No. 7, Temple Road").build();

        for (UpdateOrderRequest bad : new UpdateOrderRequest[] {unsafe, overweight, badPhone}) {
            mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fine)))
                .andExpect(status().isOk());
    }
}
