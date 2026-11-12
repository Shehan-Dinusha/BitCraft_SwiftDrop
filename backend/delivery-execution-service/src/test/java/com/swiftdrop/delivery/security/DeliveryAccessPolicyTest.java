package com.swiftdrop.delivery.security;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.UnauthenticatedCallerException;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryAccessPolicyTest {

    @Test
    @DisplayName("Courier role can access delivery session when courierId matches caller userId")
    void testCourierCanAccessOwnSession() {
        UUID courierId = UUID.randomUUID();
        CallerContext courier = new CallerContext(courierId, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setCourierId(courierId);

        assertThatCode(() -> DeliveryAccessPolicy.assertCanAccess(session, courier))
                .doesNotThrowAnyException();

        assertThatCode(() -> DeliveryAccessPolicy.assertCanAccess(courierId, courier))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Courier role is forbidden from accessing another courier's delivery session")
    void testCourierCannotAccessOtherCourierSession() {
        UUID courierA = UUID.randomUUID();
        UUID courierB = UUID.randomUUID();
        CallerContext courierCaller = new CallerContext(courierA, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setCourierId(courierB);

        assertThatThrownBy(() -> DeliveryAccessPolicy.assertCanAccess(session, courierCaller))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Courier does not own this delivery");

        assertThatThrownBy(() -> DeliveryAccessPolicy.assertCanAccess(courierB, courierCaller))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Courier does not own this delivery resource");
    }

    @Test
    @DisplayName("Courier role is forbidden when delivery session has no assigned courier")
    void testCourierCannotAccessUnassignedSession() {
        UUID courierId = UUID.randomUUID();
        CallerContext courierCaller = new CallerContext(courierId, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setCourierId(null);

        assertThatThrownBy(() -> DeliveryAccessPolicy.assertCanAccess(session, courierCaller))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Dispatcher role bypasses courier ownership checks")
    void testDispatcherBypassesOwnershipCheck() {
        UUID dispatcherId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        CallerContext dispatcher = new CallerContext(dispatcherId, UserRole.DISPATCHER, null, SriLankanDistrict.COLOMBO);

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setCourierId(courierId);

        assertThatCode(() -> DeliveryAccessPolicy.assertCanAccess(session, dispatcher))
                .doesNotThrowAnyException();

        assertThatCode(() -> DeliveryAccessPolicy.assertCanAccess(courierId, dispatcher))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("System admin role bypasses courier ownership checks")
    void testSystemAdminBypassesOwnershipCheck() {
        UUID adminId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        CallerContext admin = new CallerContext(adminId, UserRole.SYSTEM_ADMIN, null, SriLankanDistrict.COLOMBO);

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setCourierId(courierId);

        assertThatCode(() -> DeliveryAccessPolicy.assertCanAccess(session, admin))
                .doesNotThrowAnyException();

        assertThatCode(() -> DeliveryAccessPolicy.assertCanAccess(courierId, admin))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "MERCHANT"})
    @DisplayName("Non-operational roles are forbidden from accessing courier delivery resources")
    void testNonOperationalRolesForbidden(UserRole role) {
        UUID userId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        CallerContext nonOperational = new CallerContext(userId, role, null, SriLankanDistrict.COLOMBO);

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setCourierId(courierId);

        assertThatThrownBy(() -> DeliveryAccessPolicy.assertCanAccess(session, nonOperational))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized for courier delivery operations");
    }

    @Test
    @DisplayName("Missing caller context throws UnauthenticatedCallerException")
    void testMissingCallerContextThrowsUnauthenticated() {
        DeliverySession session = new DeliverySession();
        session.setCourierId(UUID.randomUUID());

        assertThatThrownBy(() -> DeliveryAccessPolicy.assertCanAccess(session, null))
                .isInstanceOf(UnauthenticatedCallerException.class)
                .hasMessageContaining("Caller context is required");
    }

    @Test
    @DisplayName("Null session throws ForbiddenException")
    void testNullSessionThrowsForbidden() {
        CallerContext courier = new CallerContext(UUID.randomUUID(), UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        assertThatThrownBy(() -> DeliveryAccessPolicy.assertCanAccess((DeliverySession) null, courier))
                .isInstanceOf(ForbiddenException.class);
    }
}

