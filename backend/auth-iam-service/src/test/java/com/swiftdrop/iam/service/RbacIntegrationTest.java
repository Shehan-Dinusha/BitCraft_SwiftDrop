package com.swiftdrop.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.dto.UserProfileResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC integration tests that verify role-based access control is correctly enforced
 * on the {@code /api/v1/users/**} endpoint family.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Unauthenticated requests → 401 Unauthorized</li>
 *   <li>CUSTOMER token → 403 Forbidden on admin-only user management endpoints</li>
 *   <li>DISPATCHER token → 403 Forbidden on SYSTEM_ADMIN-only endpoints</li>
 *   <li>MERCHANT token → 403 Forbidden on admin-only endpoints</li>
 *   <li>SYSTEM_ADMIN token → 200/201 on all user management endpoints</li>
 *   <li>Role escalation via request header is blocked</li>
 *   <li>CUSTOMER can access their own profile (/me) but not others</li>
 * </ul>
 *
 * <p>These tests run against the existing local IAM Postgres and Redis.
 * Enable with: {@code -Dswiftdrop.integration-tests=true}
 */
@EnabledIfSystemProperty(named = "swiftdrop.integration-tests", matches = "true")
@SpringBootTest(properties = {
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "swiftdrop.jwt.blacklist-prefix=jwt:m2-rbac:",
        "swiftdrop.bootstrap.admin.username=",
        "swiftdrop.bootstrap.admin.password="
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RbacIntegrationTest {

    private static final String USERS_API    = "/api/v1/users";
    private static final String COURIERS_API = "/api/v1/users/couriers";
    private static final String PASSWORD     = "RbacTest7!";

    @Autowired private UserAccountService  users;
    @Autowired private AuthenticationService authentication;
    @Autowired private TokenIssuanceService tokens;
    @Autowired private JwtConfig            jwtConfig;
    @Autowired private JdbcTemplate         jdbc;
    @Autowired private StringRedisTemplate  redis;
    @Autowired private MockMvc              mvc;
    @Autowired private ObjectMapper         json;

    /** Unique prefix for every test run so fixtures never collide with existing data. */
    private String prefix;

    /** Tokens minted during the test; cleaned up in @AfterEach. */
    private final List<TokenResponse> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        prefix = "m2rbac." + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + ".";
    }

    @AfterEach
    void cleanup() {
        // Revoke JWT blacklist keys so leaked test tokens cannot interfere.
        for (TokenResponse session : sessions) {
            var claims = tokens.parse(session.accessToken());
            redis.delete(List.of(
                    jwtConfig.getBlacklistPrefix() + "token:" + tokens.hashToken(session.accessToken()),
                    jwtConfig.getBlacklistPrefix() + "jti:"   + claims.getId()
            ));
        }
        // Remove test users' refresh tokens and accounts in dependency order.
        var ids = jdbc.queryForList(
                "select public_id from user_accounts where username like ?", UUID.class, prefix + "%");
        ids.forEach(id -> redis.delete(jwtConfig.getBlacklistPrefix() + "user:" + id));
        jdbc.update("delete from refresh_tokens where user_id in (select id from user_accounts where username like ?)", prefix + "%");
        jdbc.update("delete from role_assignments where user_id in (select id from user_accounts where username like ?)", prefix + "%");
        jdbc.update("delete from user_accounts where username like ?", prefix + "%");
    }

    // ── Unauthenticated ───────────────────────────────────────────────────────

    @Test
    @DisplayName("No token → 401 on all protected user management endpoints")
    void unauthenticated_requestsAreRejectedWith401() throws Exception {
        mvc.perform(get(USERS_API)).andExpect(status().isUnauthorized());
        mvc.perform(get(USERS_API + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post(USERS_API).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(USERS_API + "/" + UUID.randomUUID() + "/role")
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(patch(USERS_API + "/" + UUID.randomUUID() + "/status")
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    // ── CUSTOMER ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CUSTOMER token → 403 on /api/v1/users (list)")
    void customer_cannotListUsers() throws Exception {
        String bearer = bearer(fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO));
        mvc.perform(get(USERS_API).header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER token → 403 on /api/v1/users/{id} (get user by id)")
    void customer_cannotGetOtherUsersById() throws Exception {
        var customer = fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        var other    = fixture("other", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(get(USERS_API + "/" + other.id()).header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER token → 200 on /api/v1/users/me (own profile)")
    void customer_canAccessOwnProfile() throws Exception {
        var customer = fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(get(USERS_API + "/me").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("CUSTOMER token → 403 on POST /api/v1/users (create user)")
    void customer_cannotCreateUsers() throws Exception {
        var customer = fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(post(USERS_API)
                .header("Authorization", bearer(customer))
                .contentType("application/json")
                .content(json.writeValueAsString(adminCreateBody("new"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER token → 403 on PATCH /api/v1/users/{id}/role")
    void customer_cannotChangeUserRole() throws Exception {
        var customer = fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        var target   = fixture("target", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(patch(USERS_API + "/" + target.id() + "/role")
                .header("Authorization", bearer(customer))
                .contentType("application/json")
                .content("{\"role\":\"SYSTEM_ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER token → 403 on PATCH /api/v1/users/{id}/status")
    void customer_cannotChangeUserStatus() throws Exception {
        var customer = fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        var target   = fixture("target", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(patch(USERS_API + "/" + target.id() + "/status")
                .header("Authorization", bearer(customer))
                .contentType("application/json")
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
    }

    // ── DISPATCHER ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DISPATCHER token → 403 on SYSTEM_ADMIN-only endpoints")
    void dispatcher_cannotAccessAdminOnlyEndpoints() throws Exception {
        var dispatcher = fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var target     = fixture("target",   UserRole.CUSTOMER,   SriLankanDistrict.COLOMBO);
        String bearer  = bearer(dispatcher);

        // List all users — SYSTEM_ADMIN only
        mvc.perform(get(USERS_API).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        // Get user by id — SYSTEM_ADMIN only
        mvc.perform(get(USERS_API + "/" + target.id()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        // Change role — SYSTEM_ADMIN only
        mvc.perform(patch(USERS_API + "/" + target.id() + "/role")
                .header("Authorization", bearer)
                .contentType("application/json")
                .content("{\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isForbidden());
    }

    // ── MERCHANT ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MERCHANT token → 403 on all /api/v1/users management endpoints")
    void merchant_cannotAccessUserManagement() throws Exception {
        var merchant = fixture("merchant", UserRole.MERCHANT, SriLankanDistrict.COLOMBO);
        String bearer = bearer(merchant);

        mvc.perform(get(USERS_API).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post(USERS_API)
                .header("Authorization", bearer)
                .contentType("application/json")
                .content(json.writeValueAsString(adminCreateBody("new"))))
                .andExpect(status().isForbidden());
    }

    // ── SYSTEM_ADMIN ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SYSTEM_ADMIN token → 200 on GET /api/v1/users (list)")
    void admin_canListUsers() throws Exception {
        var admin  = fixture("admin",  UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        mvc.perform(get(USERS_API).param("search", prefix).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data").exists());
    }

    @Test
    @DisplayName("SYSTEM_ADMIN token → 200 on GET /api/v1/users/{id}")
    void admin_canGetUserById() throws Exception {
        var admin  = fixture("admin",  UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        var target = fixture("target", UserRole.CUSTOMER,     SriLankanDistrict.COLOMBO);
        mvc.perform(get(USERS_API + "/" + target.id()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.id").value(target.id().toString()));
    }

    @Test
    @DisplayName("SYSTEM_ADMIN token → 201 on POST /api/v1/users (create user)")
    void admin_canCreateUser() throws Exception {
        var admin = fixture("admin", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO);
        mvc.perform(post(USERS_API)
                .header("Authorization", bearer(admin))
                .contentType("application/json")
                .content(json.writeValueAsString(adminCreateBody("created"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.role").value("CUSTOMER"));
    }

    // ── Role escalation via request headers ───────────────────────────────────

    @Test
    @DisplayName("X-User-Role header cannot escalate a CUSTOMER to SYSTEM_ADMIN")
    void headerRoleEscalation_isBlocked() throws Exception {
        var customer = fixture("cust", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        mvc.perform(get(USERS_API)
                .header("Authorization", bearer(customer))
                .header("X-User-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("X-District-Id header cannot grant cross-district DISPATCHER access")
    void headerDistrictEscalation_isBlocked() throws Exception {
        var dispatcher = fixture("dispatch", UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        var galle      = fixture("galle",    UserRole.COURIER,    SriLankanDistrict.GALLE);
        mvc.perform(get(COURIERS_API + "/" + galle.id())
                .header("Authorization", bearer(dispatcher))
                .header("X-District-Id", "GALLE"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserProfileResponse fixture(String suffix, UserRole role, SriLankanDistrict district) {
        String u = prefix + suffix;
        return users.create(new CreateUserRequest(
                u, u + "@example.invalid", null,
                "Rbac", "Test", PASSWORD, role, district, null));
    }

    private String bearer(UserProfileResponse account) {
        TokenResponse session = authentication.login(account.username(), PASSWORD, "127.0.0.1", "rbac-test");
        sessions.add(session);
        return "Bearer " + session.accessToken();
    }

    private Map<String, Object> adminCreateBody(String suffix) {
        String u = prefix + suffix;
        return Map.of(
                "username",  u,
                "email",     u + "@example.invalid",
                "firstName", "Rbac",
                "lastName",  "Created",
                "password",  PASSWORD,
                "district",  "COLOMBO"
        );
    }
}

