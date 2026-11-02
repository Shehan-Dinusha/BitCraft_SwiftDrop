package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.CreateCourierRequest;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.UpdateCourierRequest;
import com.swiftdrop.iam.exception.ResourceNotFoundException;
import com.swiftdrop.iam.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierAccountServiceTest {
    @Mock private UserAccountRepository repository;
    @Mock private UserAccountService users;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private TokenIssuanceService tokens;
    @InjectMocks private CourierAccountService service;
    private UserAccount actor;
    private UserAccount courier;

    @BeforeEach
    void setUp() {
        actor = account(UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        courier = account(UserRole.COURIER, SriLankanDistrict.COLOMBO);
        when(users.requireUser(actor.getPublicId())).thenReturn(actor);
    }

    @Test
    void missingDistrictNeverWidensDispatcherScope() {
        actor.setDistrict(null);
        assertThatThrownBy(() -> service.get(actor.getPublicId(), courier.getPublicId())).isInstanceOf(ForbiddenException.class);
        verify(users, never()).requireUser(courier.getPublicId());
    }

    @Test
    void inactiveOperatorIsDenied() {
        actor.setStatus(UserStatus.SUSPENDED);
        assertThatThrownBy(() -> service.get(actor.getPublicId(), courier.getPublicId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonOperatorIsDenied() {
        actor.setRole(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.get(actor.getPublicId(), courier.getPublicId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void crossDistrictUpdateCannotReachPersistence() {
        courier.setDistrict(SriLankanDistrict.GALLE);
        when(users.requireUser(courier.getPublicId())).thenReturn(courier);
        assertThatThrownBy(() -> service.update(actor.getPublicId(), courier.getPublicId(), contact()))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void nonCourierTargetIsNotExposed() {
        courier.setRole(UserRole.SYSTEM_ADMIN);
        when(users.requireUser(courier.getPublicId())).thenReturn(courier);
        assertThatThrownBy(() -> service.get(actor.getPublicId(), courier.getPublicId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creationAlwaysUsesCourierRoleWithoutMerchantAssociation() {
        service.create(actor.getPublicId(), new CreateCourierRequest("courier", "courier@example.com", null,
                "Courier", "Test", "Password7", SriLankanDistrict.COLOMBO));
        var request = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(users).create(request.capture());
        assertThat(request.getValue().role()).isEqualTo(UserRole.COURIER);
        assertThat(request.getValue().merchantId()).isNull();
        verify(repository).lockAccountAdministration();
    }

    @Test
    void contactEditPreservesRoleAndDistrictAndRevokesStaleEmailClaims() {
        when(users.requireUser(courier.getPublicId())).thenReturn(courier);
        service.update(actor.getPublicId(), courier.getPublicId(), contact());
        assertThat(courier.getEmail()).isEqualTo("edited@example.com");
        assertThat(courier.getRole()).isEqualTo(UserRole.COURIER);
        assertThat(courier.getDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(courier.getPhone()).isNull();
        verify(refreshTokens).revokeAll(courier);
        verify(tokens).blacklistUser(courier.getPublicId());
    }

    @Test
    void unchangedEmailDoesNotRevokeSessions() {
        courier.setEmail("edited@example.com");
        when(users.requireUser(courier.getPublicId())).thenReturn(courier);
        service.update(actor.getPublicId(), courier.getPublicId(), contact());
        verifyNoInteractions(refreshTokens, tokens);
    }

    @Test
    void duplicateEmailCannotOverwriteCourier() {
        when(users.requireUser(courier.getPublicId())).thenReturn(courier);
        when(repository.existsByEmailIgnoreCaseAndPublicIdNot("edited@example.com", courier.getPublicId())).thenReturn(true);
        assertThatThrownBy(() -> service.update(actor.getPublicId(), courier.getPublicId(), contact()))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    private UpdateCourierRequest contact() {
        return new UpdateCourierRequest("Edited@Example.com", " ", "Edited", "Courier");
    }

    private UserAccount account(UserRole role, SriLankanDistrict district) {
        var account = new UserAccount();
        account.setPublicId(UUID.randomUUID());
        account.setRole(role);
        account.setDistrict(district);
        account.setStatus(UserStatus.ACTIVE);
        account.setEmail("original@example.com");
        return account;
    }
}
