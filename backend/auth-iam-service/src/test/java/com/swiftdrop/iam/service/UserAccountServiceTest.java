package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.exception.AuthException;
import com.swiftdrop.iam.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicy passwordPolicy;
    @Mock
    private TokenIssuanceService tokenIssuanceService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserAccountService userAccountService;

    private UserAccount admin;

    @BeforeEach
    void setUp() {
        admin = new UserAccount();
        admin.setId(1L);
        admin.setPublicId(UUID.randomUUID());
        admin.setUsername("root");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.SYSTEM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setCreatedAt(Instant.now());
    }

    @Test
    void registerRejectsStaffRole() {
        RegisterUserRequest request = new RegisterUserRequest(
                "newadmin", "newadmin@example.com", null, "New", "Admin",
                "Secret123", UserRole.SYSTEM_ADMIN, SriLankanDistrict.COLOMBO, null);

        assertThatThrownBy(() -> userAccountService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CUSTOMER and MERCHANT");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userAccountRepository.existsByUsernameIgnoreCase("alice")).thenReturn(true);
        RegisterUserRequest request = new RegisterUserRequest(
                "alice", "alice@example.com", null, "Alice", "Perera",
                "Secret123", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO, null);

        assertThatThrownBy(() -> userAccountService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void registerNormalisesEmailAndPersistsCustomer() {
        when(userAccountRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userAccountRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded");
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterUserRequest request = new RegisterUserRequest(
                "alice", "Alice@Example.com", " ", "Alice", "Perera",
                "Secret123", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO, null);

        var profile = userAccountService.register(request);

        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.email()).isEqualTo("alice@example.com");
        assertThat(profile.phone()).isNull();
        assertThat(profile.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(profile.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    void registeringAgainWithAnUnverifiedEmailReplacesThePendingSignUp() {
        UserAccount pending = new UserAccount();
        pending.setId(42L);
        pending.setPublicId(UUID.randomUUID());
        pending.setUsername("old.name");
        pending.setEmail("alice@example.com");
        pending.setPasswordHash("attacker-hash");
        pending.setRole(UserRole.CUSTOMER);
        pending.setStatus(UserStatus.ACTIVE);
        pending.setEmailVerified(false);
        pending.setCreatedAt(Instant.now());

        when(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(pending));
        when(userAccountRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("owner-hash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var profile = userAccountService.register(new RegisterUserRequest(
                "alice", "alice@example.com", null, "Alice", "Perera",
                "Secret123", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO, null));

        assertThat(profile.id()).isEqualTo(pending.getPublicId());
        assertThat(pending.getPasswordHash()).isEqualTo("owner-hash");
        assertThat(pending.getUsername()).isEqualTo("alice");
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    void registeringWithAVerifiedEmailIsRejected() {
        UserAccount verified = new UserAccount();
        verified.setId(43L);
        verified.setPublicId(UUID.randomUUID());
        verified.setEmail("alice@example.com");
        verified.setStatus(UserStatus.ACTIVE);
        verified.setEmailVerified(true);
        when(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(verified));
        when(userAccountRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userAccountService.register(new RegisterUserRequest(
                "alice2", "alice@example.com", null, "Alice", "Perera",
                "Secret123", UserRole.CUSTOMER, SriLankanDistrict.COLOMBO, null)))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void usersCreatedByAnAdministratorAreVerifiedImmediately() {
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var profile = userAccountService.create(new CreateUserRequest(
                "courier.one", "courier.one@example.com", null, "Courier", "One",
                "Secret123", UserRole.COURIER, SriLankanDistrict.COLOMBO, null));

        assertThat(profile.emailVerified()).isTrue();
    }

    @Test
    void cannotDemoteLastActiveSystemAdmin() {
        when(userAccountRepository.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> userAccountService.updateRole(admin.getPublicId(), UserRole.DISPATCHER))
                .isInstanceOf(AuthException.class)
                .satisfies(exception -> assertThat(((AuthException) exception).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(userAccountRepository, never()).save(admin);
    }

    @Test
    void cannotDeactivateLastActiveSystemAdmin() {
        when(userAccountRepository.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
        when(userAccountRepository.countByRoleAndStatus(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> userAccountService.updateStatus(admin.getPublicId(), UserStatus.SUSPENDED))
                .isInstanceOf(AuthException.class)
                .satisfies(exception -> assertThat(((AuthException) exception).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void existingAccountRoleCannotBeChanged() {
        UserAccount user = new UserAccount();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("courier");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        when(userAccountRepository.findByPublicId(user.getPublicId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userAccountService.updateRole(user.getPublicId(), UserRole.COURIER))
                .isInstanceOf(AuthException.class)
                .satisfies(exception -> assertThat(((AuthException) exception).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        verify(userAccountRepository, never()).save(user);
        verifyNoInteractions(refreshTokenService, tokenIssuanceService);
    }

    @Test
    void sameRoleRequestIsAnIdempotentNoOp() {
        when(userAccountRepository.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
        assertThat(userAccountService.updateRole(admin.getPublicId(), UserRole.SYSTEM_ADMIN).role())
                .isEqualTo(UserRole.SYSTEM_ADMIN);
        verify(userAccountRepository, never()).save(admin);
        verifyNoInteractions(refreshTokenService, tokenIssuanceService);
    }

    @Test
    void changePasswordRevokesSessions() {
        when(userAccountRepository.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("OldPass123", "hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");
        when(userAccountRepository.save(admin)).thenReturn(admin);

        userAccountService.changePassword(admin.getPublicId(),
                new ChangePasswordRequest("OldPass123", "NewPass123"));

        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        verify(passwordPolicy).validate("NewPass123", admin.getUsername(), admin.getEmail());
        verify(refreshTokenService).revokeAll(admin);
        verify(tokenIssuanceService).blacklistUser(admin.getPublicId());
    }
}
