package com.swiftdrop.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.exception.AuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression tests against existing IAM PostgreSQL/Redis; no containers are created. */
@EnabledIfSystemProperty(named = "swiftdrop.integration-tests", matches = "true")
@SpringBootTest(properties = {
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "swiftdrop.jwt.blacklist-prefix=jwt:m2-accounts:",
        "swiftdrop.bootstrap.admin.username=",
        "swiftdrop.bootstrap.admin.password="
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AccountPermissionsIntegrationTest {
    private static final String PASSWORD = "AccountTest7!";
    private static final String COURIERS = "/api/v1/users/couriers";
    @Autowired private UserAccountService users;
    @Autowired private AuthenticationService authentication;
    @Autowired private TokenIssuanceService tokens;
    @Autowired private JwtConfig jwtConfig;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    private String prefix;
    private final List<TokenResponse> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        prefix = "m2acct." + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".";
    }

    @AfterEach
    void cleanupOnlyOwnFixtures() {
        for (TokenResponse session : sessions) {
            var claims = tokens.parse(session.accessToken());
            redis.delete(List.of(jwtConfig.getBlacklistPrefix() + "token:" + tokens.hashToken(session.accessToken()),
                    jwtConfig.getBlacklistPrefix() + "jti:" + claims.getId()));
        }
        var ids = jdbc.queryForList("select public_id from user_accounts where username like ?", UUID.class, prefix + "%");
        ids.forEach(id -> redis.delete(jwtConfig.getBlacklistPrefix() + "user:" + id));
        jdbc.update("delete from refresh_tokens where user_id in (select id from user_accounts where username like ?)", prefix + "%");
        jdbc.update("delete from role_assignments where user_id in (select id from user_accounts where username like ?)", prefix + "%");
        jdbc.update("delete from user_accounts where username like ?", prefix + "%");
    }

    @Test
    void dispatcherCanCreateEditAndDeactivateOnlyOwnDistrictCouriers() throws Exception {
        var dispatcher = fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var other = fixture("other", UserRole.COURIER, SriLankanDistrict.GALLE);
        String bearer = bearer(dispatcher);
        String response = mvc.perform(post(COURIERS).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(createBody("created", "COLOMBO"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.role").value("COURIER"))
                .andExpect(jsonPath("data.merchantId").isEmpty()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).path("data").path("id").asText();
        mvc.perform(put(COURIERS + "/" + id).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", prefix + "edited@example.invalid", "firstName", "Edited", "lastName", "Courier"))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.firstName").value("Edited"));
        mvc.perform(patch(COURIERS + "/" + id + "/status").header("Authorization", bearer).contentType("application/json")
                .content("{\"status\":\"DEACTIVATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("DEACTIVATED"));
        mvc.perform(get(COURIERS + "/" + other.id()).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(put(COURIERS + "/" + other.id()).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", "other@example.invalid", "firstName", "Other", "lastName", "Test"))))
                .andExpect(status().isForbidden());
        mvc.perform(patch(COURIERS + "/" + other.id() + "/status").header("Authorization", bearer).contentType("application/json")
                .content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isForbidden());
        mvc.perform(post(COURIERS).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(createBody("forbidden", "GALLE")))).andExpect(status().isForbidden());
        assertThat(users.getProfile(other.id()).status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void listIsScopedAndAdministratorsCanManageOtherDistricts() throws Exception {
        var dispatcher = fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var own = fixture("own", UserRole.COURIER, SriLankanDistrict.COLOMBO);
        var other = fixture("other", UserRole.COURIER, SriLankanDistrict.GALLE);
        var customer = fixture("customer", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        var admin = fixture("admin", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        mvc.perform(get(COURIERS).param("search", prefix).header("Authorization", bearer(dispatcher)))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content.length()").value(1))
                .andExpect(jsonPath("data.content[0].id").value(own.id().toString()));
        String adminBearer = bearer(admin);
        mvc.perform(get(COURIERS).param("search", prefix).header("Authorization", adminBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content.length()").value(2));
        mvc.perform(get(COURIERS + "/" + other.id()).header("Authorization", adminBearer)).andExpect(status().isOk());
        mvc.perform(get(COURIERS + "/" + customer.id()).header("Authorization", adminBearer)).andExpect(status().isNotFound());
        mvc.perform(post(COURIERS).header("Authorization", adminBearer).contentType("application/json")
                .content(json.writeValueAsString(createBody("admincreated", "GALLE")))).andExpect(status().isCreated());
    }

    @Test
    void clientHeadersCannotImpersonateAnAdministratorOrAnotherDistrict() throws Exception {
        var customer = fixture("customer", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(get(COURIERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(COURIERS).header("Authorization", bearer(customer)).header("X-User-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isForbidden());
        var dispatcher = fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var other = fixture("other", UserRole.COURIER, SriLankanDistrict.GALLE);
        String bearer = bearer(dispatcher);
        mvc.perform(get(COURIERS + "/" + other.id()).header("Authorization", bearer)
                .header("X-User-Role", "SYSTEM_ADMIN").header("X-District-Id", "GALLE"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer)).andExpect(status().isForbidden());
        // Even a still-signed token cannot authorize a DB-demoted operator.
        jdbc.update("update user_accounts set role='CUSTOMER' where public_id=?", dispatcher.id());
        mvc.perform(get(COURIERS).header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void malformedRequestsAndCallerChosenMerchantAssociationAreRejected() throws Exception {
        var admin = fixture("admin", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        String bearer = bearer(admin);
        var missingDistrict = new java.util.LinkedHashMap<>(createBody("missing", "COLOMBO"));
        missingDistrict.remove("district");
        mvc.perform(post(COURIERS).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(missingDistrict))).andExpect(status().isBadRequest());
        mvc.perform(post(COURIERS).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(createBody("invalid", "UNKNOWN")))).andExpect(status().isBadRequest());
        var claim = new java.util.LinkedHashMap<>(createBody("merchant", "COLOMBO"));
        claim.put("role", "MERCHANT");
        claim.put("merchantId", UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsString(claim)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users").header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(claim))).andExpect(status().isBadRequest());
        claim.remove("merchantId");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsString(claim)))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.merchantId").isEmpty());
    }

    @Test
    void databaseRejectsDuplicateActiveDispatchersIncludingReactivationAndPromotion() {
        var first = fixture("dispatch1", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        assertThatThrownBy(() -> fixture("duplicate", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO))
                .isInstanceOf(DataIntegrityViolationException.class);
        users.updateStatus(first.id(), UserStatus.SUSPENDED);
        fixture("dispatch2", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        assertThatThrownBy(() -> users.updateStatus(first.id(), UserStatus.ACTIVE)).isInstanceOf(DataIntegrityViolationException.class);
        var customer = fixture("customer", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        assertThatThrownBy(() -> users.updateRole(customer.id(), UserRole.DISPATCHER)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.getProfile(first.id()).status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(users.getProfile(customer.id()).role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void emailChangeAndSuspensionInvalidateExistingCourierSessions() throws Exception {
        var dispatcher = fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var courier = fixture("courier", UserRole.COURIER, SriLankanDistrict.COLOMBO);
        String original = bearer(courier);
        String operator = bearer(dispatcher);
        mvc.perform(put(COURIERS + "/" + courier.id()).header("Authorization", operator).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", prefix + "changed@example.invalid", "firstName", "New", "lastName", "Name"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", original)).andExpect(status().isUnauthorized());
        String fresh = bearer(courier);
        mvc.perform(get("/api/v1/users/me").header("Authorization", fresh)).andExpect(status().isOk());
        mvc.perform(patch(COURIERS + "/" + courier.id() + "/status").header("Authorization", operator)
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", fresh)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens r join user_accounts u on r.user_id=u.id where u.public_id=? and r.revoked=false", Long.class, courier.id())).isZero();
    }

    @Test
    void concurrentAdministrativeChangesCannotRemoveEveryActiveAdministrator() throws Exception {
        // Do not change any real account. This regression requires an otherwise empty local admin set.
        assumeTrue(jdbc.queryForObject("select count(*) from user_accounts where role='SYSTEM_ADMIN' and status='ACTIVE'", Long.class) == 0);
        var first = fixture("admin1", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        var second = fixture("admin2", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> deactivateAfter(start, first.id()));
            var two = executor.submit(() -> deactivateAfter(start, second.id()));
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from user_accounts where username like ? and role='SYSTEM_ADMIN' and status='ACTIVE'", Long.class, prefix + "%")).isEqualTo(1);
        var remaining = users.getProfile(first.id()).status() == UserStatus.ACTIVE ? first : second;
        String bearer = bearer(remaining);
        mvc.perform(patch("/api/v1/users/" + remaining.id() + "/role").header("Authorization", bearer)
                .contentType("application/json").content("{\"role\":\"CUSTOMER\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/users/" + remaining.id() + "/status").header("Authorization", bearer)
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isConflict());
    }

    private int deactivateAfter(CountDownLatch start, UUID id) throws InterruptedException {
        start.await();
        try {
            users.updateStatus(id, UserStatus.DEACTIVATED);
            return 200;
        } catch (AuthException exception) {
            return exception.getStatus().value();
        }
    }

    @Test
    void simultaneousDispatcherCreationHasExactlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> createDispatcherAfter(start, "dispatch1"));
            var two = executor.submit(() -> createDispatcherAfter(start, "dispatch2"));
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void dispatcherConflictIs409AtTheHttpBoundary() throws Exception {
        fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var admin = fixture("admin", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        var duplicate = new java.util.LinkedHashMap<>(createBody("duplicate", "COLOMBO"));
        duplicate.put("role", "DISPATCHER");
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(admin)).contentType("application/json")
                .content(json.writeValueAsString(duplicate))).andExpect(status().isConflict());
    }

    @Test
    void legacyRoleAssignmentsCannotGrantAuthority() throws Exception {
        var customer = fixture("customer", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        jdbc.update("insert into role_assignments(role_name,user_id) select 'SYSTEM_ADMIN',id from user_accounts where public_id=?", customer.id());
        String bearer = bearer(customer);
        var claims = tokens.parse(bearer.substring("Bearer ".length()));
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        mvc.perform(get("/api/v1/users").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    private int createDispatcherAfter(CountDownLatch start, String suffix) throws InterruptedException {
        start.await();
        try {
            fixture(suffix, UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
            return 201;
        } catch (DataIntegrityViolationException exception) {
            return 409;
        }
    }

    @Test
    void liveOpenApiDescribesMerchantAssociationAsNullOnly() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var schemas = json.readTree(result.getResponse().getContentAsString()).path("components").path("schemas");
        for (String name : List.of("CreateUserRequest", "RegisterUserRequest")) {
            var property = schemas.path(name).path("properties").path("merchantId");
            var type = property.path("type");
            // OpenAPI 3.1 permits a single type or an array of types.
            if (type.isArray()) {
                assertThat(type.size()).isEqualTo(1);
                assertThat(type.get(0).asText()).isEqualTo("null");
            } else {
                assertThat(type.asText()).as(name + " schema: " + property).isEqualTo("null");
            }
        }
    }

    private UserProfileResponse fixture(String suffix, UserRole role, SriLankanDistrict district) {
        String username = prefix + suffix;
        return users.create(new CreateUserRequest(username, username + "@example.invalid", null,
                "Account", "Test", PASSWORD, role, district, null));
    }

    private String bearer(UserProfileResponse account) {
        TokenResponse session = authentication.login(account.username(), PASSWORD, "127.0.0.1", "account-regression");
        sessions.add(session);
        return "Bearer " + session.accessToken();
    }

    private Map<String, Object> createBody(String suffix, String district) {
        String username = prefix + suffix;
        return Map.of("username", username, "email", username + "@example.invalid", "firstName", "Courier",
                "lastName", "Test", "password", PASSWORD, "district", district);
    }
}
