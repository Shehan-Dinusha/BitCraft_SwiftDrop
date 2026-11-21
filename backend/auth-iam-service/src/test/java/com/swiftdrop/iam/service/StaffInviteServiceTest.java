package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.events.auth.AuthStaffInviteEvent;
import com.swiftdrop.iam.config.OtpProperties;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
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
class StaffInviteServiceTest {

    @Mock private UserAccountRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicy passwordPolicy;
    @Mock private StaffInvitePublisher publisher;

    private StaffInviteService service;
    private UserAccount courier;

    @BeforeEach
    void setUp() {
        service = new StaffInviteService(repository, passwordEncoder, passwordPolicy, publisher, new OtpProperties(),
                "https://app.example.test/", 72);
        courier = new UserAccount();
        courier.setPublicId(UUID.randomUUID());
        courier.setUsername("kasun.colombo");
        courier.setEmail("kasun@example.com");
        courier.setFirstName("Kasun");
        courier.setRole(UserRole.COURIER);
        courier.setDistrict(SriLankanDistrict.COLOMBO);
        courier.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void sendInviteStoresOnlyTheTokenHashAndEmailsALinkWithTheTokenInTheFragment() {
        String token = sendAndCaptureToken();

        assertThat(courier.getInviteTokenHash()).isEqualTo(StaffInviteService.sha256(token)).isNotEqualTo(token);
        assertThat(courier.isInvitePending()).isTrue();
        assertThat(courier.getInviteExpiresAt())
                .isBetween(Instant.now().plus(Duration.ofHours(71)), Instant.now().plus(Duration.ofHours(73)));
    }

    @Test
    void anInactiveAccountCannotBeInvited() {
        courier.setStatus(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.sendInvite(courier))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(publisher, never()).publish(any());
    }

    @Test
    void resendIsRefusedOnceTheAccountIsSetUp() {
        assertThatThrownBy(() -> service.resendInvite(courier))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void acceptingSetsThePasswordAndSpendsTheInvite() {
        String token = sendAndCaptureToken();
        when(repository.findByInviteTokenHash(StaffInviteService.sha256(token))).thenReturn(Optional.of(courier));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("encoded");
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount accepted = service.accept(token, "NewPassword1");

        assertThat(accepted.getPasswordHash()).isEqualTo("encoded");
        assertThat(accepted.isInvitePending()).isFalse();
        assertThat(accepted.getInviteExpiresAt()).isNull();
        verify(passwordPolicy).validate("NewPassword1", "kasun.colombo", "kasun@example.com");
    }

    @Test
    void anExpiredInviteIsRejected() {
        String token = sendAndCaptureToken();
        courier.setInviteExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByInviteTokenHash(StaffInviteService.sha256(token))).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> service.accept(token, "NewPassword1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void aSuspendedAccountCannotAcceptItsInvite() {
        String token = sendAndCaptureToken();
        courier.setStatus(UserStatus.SUSPENDED);
        when(repository.findByInviteTokenHash(StaffInviteService.sha256(token))).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> service.preview(token)).isInstanceOf(AuthException.class);
    }

    @Test
    void aMalformedTokenIsRejectedWithoutALookup() {
        assertThatThrownBy(() -> service.preview("not-a-token")).isInstanceOf(AuthException.class);
        verify(repository, never()).findByInviteTokenHash(any());
    }

    private String sendAndCaptureToken() {
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.sendInvite(courier);
        ArgumentCaptor<AuthStaffInviteEvent> event = ArgumentCaptor.forClass(AuthStaffInviteEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().recipientId()).isEqualTo(courier.getPublicId());
        assertThat(event.getValue().inviteUrl()).startsWith("https://app.example.test/invite#token=");
        return event.getValue().inviteUrl().substring("https://app.example.test/invite#token=".length());
    }
}
