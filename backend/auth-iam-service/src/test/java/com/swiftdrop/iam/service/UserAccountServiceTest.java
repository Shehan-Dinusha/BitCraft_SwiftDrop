package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
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
    }

    @Test
    void cannotDemoteLastActiveSystemAdmin() {
        when(userAccountRepository.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
        when(userAccountRepository.countByRoleAndStatus(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

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
    void roleChangeRevokesSessions() {
        UserAccount user = new UserAccount();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("courier");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        when(userAccountRepository.findByPublicId(user.getPublicId())).thenReturn(Optional.of(user));
        when(userAccountRepository.save(user)).thenReturn(user);

        userAccountService.updateRole(user.getPublicId(), UserRole.COURIER);

        assertThat(user.getRole()).isEqualTo(UserRole.COURIER);
        verify(refreshTokenService).revokeAll(user);
        verify(tokenIssuanceService).blacklistUser(user.getPublicId());
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
        verify(passwordPolicy).validate("NewPass123");
        verify(refreshTokenService).revokeAll(admin);
        verify(tokenIssuanceService).blacklistUser(admin.getPublicId());
    }
}
