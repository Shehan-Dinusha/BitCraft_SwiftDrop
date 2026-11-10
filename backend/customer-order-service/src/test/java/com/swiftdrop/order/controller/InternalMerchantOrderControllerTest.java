package com.swiftdrop.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.InternalCreateOrderRequest;
import com.swiftdrop.order.exception.PricingUnavailableException;
import com.swiftdrop.order.service.InternalOrderService;
import com.swiftdrop.order.service.InternalTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(InternalOrderController.class)
class InternalMerchantOrderControllerTest {

    private static final UUID MERCHANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InternalOrderService internalOrderService;

    @MockitoBean
    private InternalTrackingService internalTrackingService;

    private CustomerOrderResponse savedOrder;

    @BeforeEach
    void setUp() {
        savedOrder = CustomerOrderResponse.builder().orderId(UUID.randomUUID()).trackingNumber("SD-7K4M-9QX2").build();
    }

    private InternalCreateOrderRequest valid() {
        return new InternalCreateOrderRequest(MERCHANT_ID, "SHOP-2026-000123",
                "No. 42, Galle Road", null, "Colombo 03", SriLankanDistrict.COLOMBO, "00300", "Sunil Perera", "+94771234567",
                "No. 18, Peradeniya Road", null, "Kandy", SriLankanDistrict.KANDY, "20000", "Kasun Perera", "0711234567",
                null, null, null, null, new BigDecimal("2.4"), null, "Batik sarees", null, null, true);
    }

    private ResultActions submit(UnaryOperator<String> mutateJson) throws Exception {
        String json = mutateJson.apply(objectMapper.writeValueAsString(valid()));
        return mockMvc.perform(post("/internal/v1/orders").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("A new merchant order returns 201 with the order")
    void newOrderReturns201() throws Exception {
        when(internalOrderService.createMerchantOrder(any(), any()))
                .thenReturn(new InternalOrderService.CreateResult(savedOrder, true));

        submit(json -> json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("SD-7K4M-9QX2"));
    }

    @Test
    @DisplayName("Repeating the same external reference returns 200 with the same order")
    void repeatReturns200() throws Exception {
        when(internalOrderService.createMerchantOrder(any(), any()))
                .thenReturn(new InternalOrderService.CreateResult(savedOrder, false));

        submit(json -> json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value("SD-7K4M-9QX2"));
    }

    @Test
    @DisplayName("Recipient email, dimensions, speed and cod are optional")
    void optionalFieldsMayBeMissing() throws Exception {
        when(internalOrderService.createMerchantOrder(any(), any()))
                .thenReturn(new InternalOrderService.CreateResult(savedOrder, true));

        submit(json -> json).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Missing merchant id, external reference, address or weight returns a safe 400")
    void missingRequiredFieldsReturn400() throws Exception {
        for (String field : new String[] {"merchantId", "externalRef", "pickupAddressLine1", "deliveryCity",
                "recipientPhone", "actualWeightKg", "prohibitedItemsDeclared"}) {
            submit(json -> json.replaceAll("\"" + field + "\":(\"[^\"]*\"|[^,}]*)", "\"" + field + "\":null"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(field)));
        }
    }

    @ParameterizedTest(name = "[{index}] unsafe external reference is rejected")
    @ValueSource(strings = {"<script>alert(1)</script>", "REF' OR 1=1 --", "ref with spaces", "REF;DROP"})
    void unsafeExternalRefReturns400(String ref) throws Exception {
        String body = submit(json -> json.replace("SHOP-2026-000123", ref.replace("\"", "")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("script", "OR 1=1", "DROP", "spaces");
    }

    @Test
    @DisplayName("Unsafe address text, bad phone, and out-of-range weight return 400 without echoing the input")
    void invalidValuesReturn400WithoutEchoing(CapturedOutput output) throws Exception {
        String body = submit(json -> json
                .replace("No. 42, Galle Road", "77 Leaky Lane <b>x</b>")
                .replace("+94771234567", "0719998887x")
                .replace("2.4", "51"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("pickupAddressLine1", "pickupContactPhone", "actualWeightKg")
                .doesNotContain("Leaky", "0719998887", "<b>");
        assertThat(output.getAll()).doesNotContain("Leaky", "0719998887");
    }

    @Test
    @DisplayName("Pricing outage returns 503 and nothing else")
    void pricingOutageReturns503() throws Exception {
        when(internalOrderService.createMerchantOrder(any(), any())).thenThrow(new PricingUnavailableException());

        submit(json -> json)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Pricing service is temporarily unavailable"));
    }
}
