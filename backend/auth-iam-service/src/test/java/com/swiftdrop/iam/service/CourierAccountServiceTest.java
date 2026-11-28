package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.dto.CreateCourierRequest;
import com.swiftdrop.iam.exception.AuthException;
import com.swiftdrop.iam.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierAccountServiceTest {

    @Mock
    private UserAccountService userAccountService;

    @InjectMocks
    private CourierAccountService service;

    @Test
    void aDispatcherRegistersCouriersInTheirOwnDistrict() {
        UserAccount dispatcher = account(UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        UserAccount created = account(UserRole.COURIER, SriLankanDistrict.COLOMBO);
        when(userAccountService.requireUser(dispatcher.getPublicId())).thenReturn(dispatcher);
        when(userAccountService.createInvited(any(), any(), any(), any(), any(), eq(UserRole.COURIER),
                eq(SriLankanDistrict.COLOMBO))).thenReturn(created);

        var profile = service.register(dispatcher.getPublicId(), new CreateCourierRequest(
                "kasun", "kasun@example.com", "+94771234567", "Kasun", "Perera"));

        assertThat(profile.district()).isEqualTo(SriLankanDistrict.COLOMBO);
        verify(userAccountService).createInvited("kasun", "kasun@example.com", "+94771234567", "Kasun", "Perera",
                UserRole.COURIER, SriLankanDistrict.COLOMBO);
    }

    @Test
    void anAdminDoesNotManageCouriers() {
        UserAccount admin = account(UserRole.SYSTEM_ADMIN, null);
        when(userAccountService.requireUser(admin.getPublicId())).thenReturn(admin);

        assertThatThrownBy(() -> service.register(admin.getPublicId(), new CreateCourierRequest(
                "kasun", "kasun@example.com", "+94771234567", "Kasun", "Perera")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(userAccountService, never()).createInvited(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aDispatcherCannotSuspendACourierOfAnotherDistrict() {
        UserAccount dispatcher = account(UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        UserAccount galleCourier = account(UserRole.COURIER, SriLankanDistrict.GALLE);
        when(userAccountService.requireUser(dispatcher.getPublicId())).thenReturn(dispatcher);
        when(userAccountService.requireUser(galleCourier.getPublicId())).thenReturn(galleCourier);

        assertThatThrownBy(() -> service.updateStatus(dispatcher.getPublicId(), galleCourier.getPublicId(),
                UserStatus.SUSPENDED))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(userAccountService, never()).updateStatus(any(), any());
    }

    @Test
    void aDispatcherCannotManageAccountsThatAreNotCouriers() {
        UserAccount dispatcher = account(UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        UserAccount customer = account(UserRole.CUSTOMER, SriLankanDistrict.COLOMBO);
        when(userAccountService.requireUser(dispatcher.getPublicId())).thenReturn(dispatcher);
        when(userAccountService.requireUser(customer.getPublicId())).thenReturn(customer);

        assertThatThrownBy(() -> service.updateStatus(dispatcher.getPublicId(), customer.getPublicId(),
                UserStatus.SUSPENDED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDispatcherSuspendsTheirOwnCourier() {
        UserAccount dispatcher = account(UserRole.DISPATCHER, SriLankanDistrict.COLOMBO);
        UserAccount courier = account(UserRole.COURIER, SriLankanDistrict.COLOMBO);
        when(userAccountService.requireUser(dispatcher.getPublicId())).thenReturn(dispatcher);
        when(userAccountService.requireUser(courier.getPublicId())).thenReturn(courier);

        service.updateStatus(dispatcher.getPublicId(), courier.getPublicId(), UserStatus.SUSPENDED);

        verify(userAccountService).updateStatus(courier.getPublicId(), UserStatus.SUSPENDED);
    }

    @Test
    void aDispatcherListsOnlyTheirDistrict() {
        UserAccount dispatcher = account(UserRole.DISPATCHER, SriLankanDistrict.KANDY);
        when(userAccountService.requireUser(dispatcher.getPublicId())).thenReturn(dispatcher);

        service.list(dispatcher.getPublicId(), null, null, 0, 50);

        verify(userAccountService).list(UserRole.COURIER, null, SriLankanDistrict.KANDY, null, 0, 50);
    }

    @Test
    void otherRolesCannotManageCouriers() {
        UserAccount courier = account(UserRole.COURIER, SriLankanDistrict.COLOMBO);
        when(userAccountService.requireUser(courier.getPublicId())).thenReturn(courier);

        assertThatThrownBy(() -> service.list(courier.getPublicId(), null, null, 0, 50))
                .isInstanceOf(AuthException.class);
    }

    private static UserAccount account(UserRole role, SriLankanDistrict district) {
        UserAccount account = new UserAccount();
        account.setPublicId(UUID.randomUUID());
        account.setUsername(role.name().toLowerCase() + "-" + UUID.randomUUID());
        account.setRole(role);
        account.setDistrict(district);
        account.setStatus(UserStatus.ACTIVE);
        return account;
    }
}
