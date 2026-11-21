package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
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
            {"businessName":"Shop","businessCategory":"GIFTS_AND_CRAFTS","monthlyParcelVolume":"UP_TO_50",
             "businessRegNo":"BR-1","contactEmail":"shop@example.com","contactPhone":"+94771234567",
             "pickupAddress":"No. 12, Temple Road","pickupCity":"Nugegoda","pickupPostalCode":"10250",
             "operatingDistrict":"COLOMBO"}
            """;

    @Test void anApplicationWithInvalidDetailsIsRejectedBeforeTheService() throws Exception {
        String[][] invalid = {
                {"\"contactPhone\":\"+94771234567\"", "\"contactPhone\":\"12345\""},
                {"\"pickupPostalCode\":\"10250\"", "\"pickupPostalCode\":\"1025\""},
                {"\"businessRegNo\":\"BR-1\"", "\"businessRegNo\":\"BR-1\",\"websiteUrl\":\"not a link\""},
                {"\"businessName\":\"Shop\"", "\"businessName\":\"<b>Shop</b>\""},
                {"\"businessCategory\":\"GIFTS_AND_CRAFTS\",", ""}
        };
        for (String[] change : invalid) {
            mvc.perform(post("/api/v1/merchants/register")
                    .header("X-User-Id", id).header("X-User-Role", "MERCHANT")
                    .contentType(MediaType.APPLICATION_JSON).content(business.replace(change[0], change[1])))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(approvals);

        mvc.perform(post("/api/v1/merchants/register")
                .header("X-User-Id", id).header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON).content(business))
                .andExpect(status().isCreated());
    }

    @Test void unauthenticatedCallerIsRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/merchants/register").contentType(MediaType.APPLICATION_JSON).content(business))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/merchants/me/application")).andExpect(status().isUnauthorized());
        verifyNoInteractions(approvals);
    }

    @Test void merchantCannotUseReviewOperations() throws Exception {
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
        verify(approvals).approve(any(CallerContext.class), eq(UUID.fromString(id)), isNull());
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/reject")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Invalid business\"}"))
                .andExpect(status().isOk());
        verify(approvals).reject(any(CallerContext.class), eq(UUID.fromString(id)), eq("Invalid business"));
        mvc.perform(get("/api/v1/admin/merchants?status=PENDING_APPROVAL")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")).andExpect(status().isOk());
    }

    @Test void aDispatcherReviewsWithTheirDistrictIdentity() throws Exception {
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/approve")
                .header("X-User-Id", id).header("X-User-Role", "DISPATCHER").header("X-District-Id", "KANDY"))
                .andExpect(status().isOk());
        verify(approvals).approve(argThat(caller -> caller.role() == UserRole.DISPATCHER
                && caller.districtId() == com.swiftdrop.contracts.enums.SriLankanDistrict.KANDY), any(), any());
    }

    @Test void onlyAnAdminCanSuspendOrReactivate() throws Exception {
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/suspend")
                .header("X-User-Id", id).header("X-User-Role", "DISPATCHER").header("X-District-Id", "KANDY"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/suspend")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/merchants/" + id + "/reactivate")
                .header("X-User-Id", id).header("X-User-Role", "SYSTEM_ADMIN")).andExpect(status().isOk());
        verify(approvals).suspend(UUID.fromString(id));
        verify(approvals).reactivate(UUID.fromString(id));
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
        mvc.perform(get("/api/v1/merchants/me/application").header("X-User-Id", id).header("X-User-Role", "MERCHANT"))
                .andExpect(status().isOk());
        verify(approvals).myApplication(UUID.fromString(id));
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
