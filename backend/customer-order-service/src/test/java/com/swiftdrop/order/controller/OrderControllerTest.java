package com.swiftdrop.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.dto.CancelOrderRequest;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.service.CustomerOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerOrderService customerOrderService;

    @Test
    @DisplayName("GET /api/v1/orders/health returns 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/orders/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/orders/estimate returns fee estimate response (FR-C2)")
    void testEstimateEndpoint() throws Exception {
        FeeEstimateResponse estimate = FeeEstimateResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .chargeableWeightKg(new BigDecimal("1.50"))
                .estimatedFee(new BigDecimal("610.00"))
                .currency("LKR")
                .pricingRuleVersionId("PRICING-2026-V1")
                .build();

        when(customerOrderService.calculateEstimate(any(FeeEstimateRequest.class))).thenReturn(estimate);

        FeeEstimateRequest req = FeeEstimateRequest.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .actualWeightKg(new BigDecimal("1.50"))
                .speed(OrderSpeed.STANDARD)
                .build();

        mockMvc.perform(post("/api/v1/orders/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.estimatedFee").value(610.00))
                .andExpect(jsonPath("$.data.currency").value("LKR"));
    }

    @Test
    @DisplayName("POST /api/v1/orders creates order successfully (FR-C1)")
    void testCreateOrderEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        CustomerOrderResponse response = CustomerOrderResponse.builder()
                .orderId(orderId)
                .trackingNumber("SD-7K4M-9QX2")
                .status(PublicOrderStatus.CREATED)
                .estimatedFee(new BigDecimal("610.00"))
                .finalFee(new BigDecimal("610.00"))
                .currency("LKR")
                .build();

        when(customerOrderService.createOrder(any(CreateOrderRequest.class), any(), any())).thenReturn(response);

        CreateOrderRequest createReq = CreateOrderRequest.builder()
                .pickupAddressLine1("No. 1, Main St")
                .pickupCity("Colombo")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00100")
                .pickupContactName("Kamal")
                .pickupContactPhone("+94771234567")
                .deliveryAddressLine1("No. 2, Cross St")
                .deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20000")
                .recipientName("Nimal")
                .recipientPhone("+94711234567")
                .recipientEmail("nimal@gmail.com")
                .lengthCm(new BigDecimal("20.0"))
                .widthCm(new BigDecimal("15.0"))
                .heightCm(new BigDecimal("10.0"))
                .actualWeightKg(new BigDecimal("1.50"))
                .speed(OrderSpeed.STANDARD)
                .prohibitedItemsDeclared(true)
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("SD-7K4M-9QX2"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/shipping-label returns application/pdf (FR-C4, FR-C5)")
    void testDownloadShippingLabel() throws Exception {
        UUID orderId = UUID.randomUUID();
        byte[] dummyPdf = "%PDF-1.4 test".getBytes();

        when(customerOrderService.getShippingLabelPdf(orderId)).thenReturn(dummyPdf);

        mockMvc.perform(get("/api/v1/orders/{orderId}/shipping-label", orderId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"shipping-label-" + orderId + ".pdf\""));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{orderId}/cancel cancels order (FR-C3)")
    void testCancelOrderEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        CustomerOrderResponse cancelledResponse = CustomerOrderResponse.builder()
                .orderId(orderId)
                .status(PublicOrderStatus.CANCELLED)
                .cancellationReason("Customer changed mind")
                .build();

        when(customerOrderService.cancelOrder(eq(orderId), any(), any(), any())).thenReturn(cancelledResponse);

        CancelOrderRequest req = CancelOrderRequest.builder().reason("Customer changed mind").build();

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{orderId}/fee-revision/reject cancels order at $0 (FR-C8)")
    void testRejectFeeRevisionEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        CustomerOrderResponse rejectedResponse = CustomerOrderResponse.builder()
                .orderId(orderId)
                .status(PublicOrderStatus.CANCELLED)
                .finalFee(BigDecimal.ZERO)
                .feeRevisionStatus(FeeRevisionStatus.REJECTED)
                .build();

        when(customerOrderService.rejectFeeRevision(eq(orderId), any(), any())).thenReturn(rejectedResponse);

        mockMvc.perform(post("/api/v1/orders/{orderId}/fee-revision/reject", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeRevisionStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.finalFee").value(0))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
