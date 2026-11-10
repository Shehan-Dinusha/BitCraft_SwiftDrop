package com.swiftdrop.contracts.security;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.swiftdrop.contracts.enums.SriLankanDistrict.COLOMBO;
import static com.swiftdrop.contracts.enums.SriLankanDistrict.KANDY;
import static com.swiftdrop.contracts.enums.UserRole.COURIER;
import static com.swiftdrop.contracts.enums.UserRole.CUSTOMER;
import static com.swiftdrop.contracts.enums.UserRole.DISPATCHER;
import static com.swiftdrop.contracts.enums.UserRole.MERCHANT;
import static com.swiftdrop.contracts.enums.UserRole.SYSTEM_ADMIN;
import static com.swiftdrop.contracts.security.JwtHeaderConstants.X_DISTRICT_ID;
import static com.swiftdrop.contracts.security.JwtHeaderConstants.X_MERCHANT_ID;
import static com.swiftdrop.contracts.security.JwtHeaderConstants.X_USER_ID;
import static com.swiftdrop.contracts.security.JwtHeaderConstants.X_USER_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerContextTest {
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_USER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID BUSINESS = UUID.fromString("abcdefab-abcd-4abc-8abc-abcdefabcdef");
    private static final UUID OTHER_BUSINESS = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void readsVerifiedIdentityUsingSharedHeaderNames() {
        Map<String, String> headers = headers(MERCHANT);
        headers.put(X_MERCHANT_ID, BUSINESS.toString());
        headers.put(X_DISTRICT_ID, "NUWARA_ELIYA");

        CallerContext caller = CallerContext.from(headers::get);

        assertThat(caller).isEqualTo(new CallerContext(USER, MERCHANT, BUSINESS, SriLankanDistrict.NUWARA_ELIYA));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void acceptsEveryCanonicalBackendRole(UserRole role) {
        assertThat(CallerContext.from(headers(role)::get).role()).isEqualTo(role);
    }

    @Test
    void absentOptionalScopesRemainAbsent() {
        CallerContext caller = CallerContext.from(headers(CUSTOMER)::get);
        assertThat(caller.merchantId()).isNull();
        assertThat(caller.districtId()).isNull();
    }

    @Test
    void trimsHeadersAndAcceptsUppercaseCanonicalUuid() {
        Map<String, String> headers = headers(MERCHANT);
        headers.put(X_USER_ID, " " + USER + " ");
        headers.put(X_USER_ROLE, " MERCHANT ");
        headers.put(X_MERCHANT_ID, " " + BUSINESS.toString().toUpperCase(java.util.Locale.ROOT) + " ");
        headers.put(X_DISTRICT_ID, " COLOMBO ");
        assertThat(CallerContext.from(headers::get)).isEqualTo(new CallerContext(USER, MERCHANT, BUSINESS, COLOMBO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingRequiredIdentity(String value) {
        for (String name : new String[]{X_USER_ID, X_USER_ROLE}) {
            Map<String, String> headers = headers(CUSTOMER);
            headers.put(name, value);
            assertThatThrownBy(() -> CallerContext.from(headers::get))
                    .isInstanceOf(UnauthenticatedCallerException.class)
                    .satisfies(error -> assertThat(((CallerContextException) error).statusCode()).isEqualTo(401));
        }
    }

    @Test
    void rejectsMissingHeaderLookupAndInvalidDirectConstruction() {
        assertThatThrownBy(() -> CallerContext.from(null)).isInstanceOf(UnauthenticatedCallerException.class);
        assertThatThrownBy(() -> new CallerContext(null, CUSTOMER, null, null)).isInstanceOf(UnauthenticatedCallerException.class);
        assertThatThrownBy(() -> new CallerContext(USER, null, null, null)).isInstanceOf(UnauthenticatedCallerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-sensitive-value", "1-1-1-1-1", "11111111111141118111111111111111", "11111111-1111-4111-8111-111111111111,22222222-2222-4222-8222-222222222222"})
    void rejectsMalformedUserAndMerchantUuidsWithoutEchoingTheirValues(String value) {
        for (String name : new String[]{X_USER_ID, X_MERCHANT_ID}) {
            Map<String, String> headers = headers(MERCHANT);
            headers.put(name, value);
            assertThatThrownBy(() -> CallerContext.from(headers::get))
                    .isInstanceOf(UnauthenticatedCallerException.class)
                    .hasMessage("Invalid " + name + " header")
                    .hasMessageNotContaining(value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN_OPS", "ADMIN", "DRIVER", "DISPATCH", "customer", "UNKNOWN", "CUSTOMER,SYSTEM_ADMIN"})
    void rejectsPersonaAliasesUnknownRolesAndCombinedRoles(String value) {
        Map<String, String> headers = headers(CUSTOMER);
        headers.put(X_USER_ROLE, value);
        assertThatThrownBy(() -> CallerContext.from(headers::get))
                .isInstanceOf(UnauthenticatedCallerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "Colombo", "00000000-0000-0000-0000-000000000000", "COLOMBO,KANDY"})
    void rejectsSuppliedMalformedDistrictRatherThanDroppingItsScope(String value) {
        Map<String, String> headers = headers(DISPATCHER);
        headers.put(X_DISTRICT_ID, value);
        assertThatThrownBy(() -> CallerContext.from(headers::get))
                .isInstanceOf(UnauthenticatedCallerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void blankOptionalHeadersAreAbsentAndNeverAuthorizeAScope(String value) {
        Map<String, String> headers = headers(MERCHANT);
        headers.put(X_MERCHANT_ID, value);
        headers.put(X_DISTRICT_ID, value);
        CallerContext caller = CallerContext.from(headers::get);
        assertThat(caller.merchantId()).isNull();
        assertThat(caller.districtId()).isNull();
        assertThatThrownBy(() -> caller.requireMerchant(BUSINESS)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> caller.requireDistrict(COLOMBO)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void roleGuardAllowsOnlyAnExplicitlyListedRole() {
        CallerContext caller = caller(COURIER, null, COLOMBO);
        assertThatCode(() -> caller.requireRole(COURIER, DISPATCHER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> caller.requireRole(SYSTEM_ADMIN)).isInstanceOf(ForbiddenException.class)
                .satisfies(error -> assertThat(((CallerContextException) error).statusCode()).isEqualTo(403));
    }

    @Test
    void emptyOrNullRoleAllowlistsDenyAccess() {
        CallerContext caller = caller(SYSTEM_ADMIN, null, null);
        assertThatThrownBy(caller::requireRole).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> caller.requireRole((UserRole[]) null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> caller.requireRole((UserRole) null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userOwnershipAllowsSelfAndOnlyExplicitPrivilegedRoles() {
        CallerContext customer = caller(CUSTOMER, null, null);
        CallerContext admin = caller(SYSTEM_ADMIN, null, null);
        assertThatCode(() -> customer.requireSelfOrRole(USER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> customer.requireSelfOrRole(OTHER_USER, SYSTEM_ADMIN)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> admin.requireSelfOrRole(OTHER_USER)).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> admin.requireSelfOrRole(OTHER_USER, SYSTEM_ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void merchantOwnershipRequiresTheCorrectRoleAndBusiness() {
        CallerContext merchant = caller(MERCHANT, BUSINESS, null);
        assertThatCode(() -> merchant.requireMerchant(BUSINESS)).doesNotThrowAnyException();
        assertThatThrownBy(() -> merchant.requireMerchant(OTHER_BUSINESS)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> caller(MERCHANT, null, null).requireMerchant(BUSINESS)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> caller(CUSTOMER, BUSINESS, null).requireMerchant(BUSINESS)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void merchantAdministratorBypassMustBeExplicit() {
        CallerContext admin = caller(SYSTEM_ADMIN, null, null);
        assertThatThrownBy(() -> admin.requireMerchant(BUSINESS)).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> admin.requireMerchant(BUSINESS, SYSTEM_ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void districtScopeRejectsCrossDistrictAndUnassignedCallers() {
        CallerContext dispatcher = caller(DISPATCHER, null, COLOMBO);
        assertThatCode(() -> dispatcher.requireDistrict(COLOMBO)).doesNotThrowAnyException();
        assertThatThrownBy(() -> dispatcher.requireDistrict(KANDY)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> caller(DISPATCHER, null, null).requireDistrict(COLOMBO)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void districtAdministratorBypassMustBeExplicit() {
        CallerContext admin = caller(SYSTEM_ADMIN, null, null);
        assertThatThrownBy(() -> admin.requireDistrict(COLOMBO)).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> admin.requireDistrict(COLOMBO, SYSTEM_ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void nullResourceOwnersAndDistrictsNeverAuthorizeEvenAnExplicitAdministrator() {
        CallerContext admin = caller(SYSTEM_ADMIN, null, null);
        assertThatThrownBy(() -> admin.requireSelfOrRole(null, SYSTEM_ADMIN)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> admin.requireMerchant(null, SYSTEM_ADMIN)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> admin.requireDistrict(null, SYSTEM_ADMIN)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void matchingOwnerOrDistrictDoesNotGrantAnOperationalRole() {
        CallerContext customer = caller(CUSTOMER, null, COLOMBO);
        assertThatCode(() -> customer.requireSelfOrRole(USER)).doesNotThrowAnyException();
        assertThatCode(() -> customer.requireDistrict(COLOMBO)).doesNotThrowAnyException();
        assertThatThrownBy(() -> customer.requireRole(COURIER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> customer.requireRole(DISPATCHER)).isInstanceOf(ForbiddenException.class);
    }

    private static Map<String, String> headers(UserRole role) {
        return new HashMap<>(Map.of(X_USER_ID, USER.toString(), X_USER_ROLE, role.name()));
    }

    private static CallerContext caller(UserRole role, UUID merchantId, SriLankanDistrict districtId) {
        return new CallerContext(USER, role, merchantId, districtId);
    }
}
