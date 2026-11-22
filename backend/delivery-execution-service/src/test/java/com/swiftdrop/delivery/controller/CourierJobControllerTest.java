package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.dto.CourierJobResponse;
import com.swiftdrop.delivery.dto.CourierJobStop;
import com.swiftdrop.delivery.exception.GlobalExceptionHandler;
import com.swiftdrop.delivery.exception.OrderDetailsUnavailableException;
import com.swiftdrop.delivery.service.CourierJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CourierJobController.class)
@Import(GlobalExceptionHandler.class)
class CourierJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourierJobService courierJobService;

    private final UUID deliveryId = UUID.randomUUID();

    @Test
    @DisplayName("Returns the courier's job and forbids caching it")
    void returnsTheJob() throws Exception {
        when(courierJobService.getJob(eq(deliveryId), any())).thenReturn(new CourierJobResponse(
                deliveryId, false, OrderSpeed.STANDARD,
                new CourierJobStop("Nimal Perera", "+94771234567", null, "12 Galle Road", null, "Colombo 03", "00300",
                        SriLankanDistrict.COLOMBO),
                null, null, "Books", null, null, null, BigDecimal.TWO, new BigDecimal("670.00"), "LKR"));

        mockMvc.perform(asCourier())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.pickup.phone").value("+94771234567"))
                .andExpect(jsonPath("$.data.feeToCollect").value(670.00))
                .andExpect(jsonPath("$.data.dropOff").doesNotExist());
    }

    @Test
    @DisplayName("Without gateway identity headers it answers 401")
    void requiresIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries/{id}/job", deliveryId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Another courier's delivery answers 403")
    void otherCourierIsForbidden() throws Exception {
        when(courierJobService.getJob(eq(deliveryId), any())).thenThrow(new ForbiddenException("Not yours"));

        mockMvc.perform(asCourier()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("When the order service does not answer, the courier is told to try again (503)")
    void orderServiceDown() throws Exception {
        when(courierJobService.getJob(eq(deliveryId), any())).thenThrow(new OrderDetailsUnavailableException());

        mockMvc.perform(asCourier())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The addresses could not be loaded. Try again."));
    }

    private MockHttpServletRequestBuilder asCourier() {
        return get("/api/v1/deliveries/{id}/job", deliveryId)
                .header(JwtHeaderConstants.X_USER_ID, UUID.randomUUID().toString())
                .header(JwtHeaderConstants.X_USER_ROLE, UserRole.COURIER.name());
    }
}
