package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.service.MerchantApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantApprovalController.class)
class ApprovalRbacTest {
    @Autowired MockMvc mvc;
    @MockitoBean MerchantApprovalService approvals;
    String id = UUID.randomUUID().toString();
    String business = """
            {"businessName":"Shop","businessRegNo":"BR-1","contactEmail":"shop@example.com",
             "contactPhone":"+94771234567","pickupAddress":"Colombo","operatingDistrict":"COLOMBO"}
            """;

    @Test void unauthenticatedCallerIsRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/merchants/register").contentType(MediaType.APPLICATION_JSON).content(business))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(approvals);
    }

    @Test void merchantCannotUseAdminOperations() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants").header("X-User-Id", id).header("X-User-Role", "MERCHANT"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/approve")
                .header("X-User-Id", id).header("X-User-Role", "MERCHANT")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/reject")
                .header("X-User-Id", id).header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Invalid business\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(approvals);
    }

    @Test void adminCanApproveRejectAndList() throws Exception {
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/approve")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")).andExpect(status().isOk());
        verify(approvals).approve(UUID.fromString(id), UUID.fromString(id), null);
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/reject")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Invalid business\"}"))
                .andExpect(status().isOk());
        verify(approvals).reject(UUID.fromString(id), "Invalid business");
        mvc.perform(get("/api/v1/admin/merchants?status=PENDING_APPROVAL")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")).andExpect(status().isOk());
    }

    @Test void rejectionRequiresReason() throws Exception {
        for (String body : new String[]{"{}", "{\"reason\":\"  \"}"}) {
            mvc.perform(post("/api/v1/admin/merchants/" + id + "/reject")
                    .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(approvals);
    }

    @Test void registrationUsesAuthenticatedOwner() throws Exception {
        mvc.perform(post("/api/v1/merchants/register").header("X-User-Id", id)
                .header("X-User-Role", "MERCHANT").contentType(MediaType.APPLICATION_JSON).content(business))
                .andExpect(status().isCreated());
        verify(approvals).register(eq(UUID.fromString(id)), any());
    }

    @Test void registrationRejectsWrongRoleAndInvalidDetails() throws Exception {
        mvc.perform(post("/api/v1/merchants/register").header("X-User-Id", id)
                .header("X-User-Role", "CUSTOMER").contentType(MediaType.APPLICATION_JSON).content(business))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/merchants/register").header("X-User-Id", id)
                .header("X-User-Role", "MERCHANT").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approvals);
    }

    @Test void malformedStatusIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants?status=INVALID").header("X-User-Id", id)
                .header("X-User-Role", "SYSTEM_ADMIN")).andExpect(status().isBadRequest());
    }
}
