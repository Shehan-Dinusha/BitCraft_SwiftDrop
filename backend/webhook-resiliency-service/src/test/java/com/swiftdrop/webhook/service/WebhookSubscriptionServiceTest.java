package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.exception.InvalidEventSubscriptionException;
import com.swiftdrop.webhook.exception.WebhookEndpointNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookSubscriptionServiceTest {

    private static final String URL = "https://merchant.example.com/webhooks";

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookDeliveryAttemptRepository attemptRepository;

    @Mock
    private OutboundWebhookDispatcher dispatcher;

    @InjectMocks
    private WebhookSubscriptionService subscriptionService;

    private UUID merchantA;
    private UUID merchantB;
    private UUID endpointId;
    private WebhookEndpoint endpointOfA;
    /** User id intentionally differs from the merchant id: they are separate identities. */
    private CallerContext callerA;
    private CallerContext callerB;
    private CallerContext admin;

    @BeforeEach
    void setUp() {
        merchantA = UUID.randomUUID();
        merchantB = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        endpointOfA = WebhookEndpoint.builder()
                .endpointId(endpointId)
                .merchantId(merchantA)
                .targetUrl(URL)
                .secretKey("whsec_abc123")
                .subscribedEvents("parcel.status.changed")
                .active(true)
                .description("Sample Endpoint")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        callerA = merchant(merchantA);
        callerB = merchant(merchantB);
        admin = caller(UserRole.SYSTEM_ADMIN, null);
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(endpointOfA));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static CallerContext merchant(UUID merchantId) {
        return caller(UserRole.MERCHANT, merchantId);
    }

    private static CallerContext caller(UserRole role, UUID merchantId) {
        return new CallerContext(UUID.randomUUID(), role, merchantId, null);
    }

    private static CreateWebhookEndpointRequest request(UUID merchantId) {
        return new CreateWebhookEndpointRequest(merchantId, URL, "parcel.status.changed,order.created", "ERP Webhook");
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("merchant registers under the merchant identity from its token, not its user id")
        void merchantCreatesUnderOwnIdentity() {
            WebhookEndpointResponse response = subscriptionService.createEndpoint(request(null), callerA);

            assertThat(response.merchantId()).isEqualTo(merchantA).isNotEqualTo(callerA.userId());
            assertThat(response.secretKey()).startsWith("whsec_");
            assertThat(response.subscribedEvents()).isEqualTo("parcel.status.changed,order.created");
            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("merchant may repeat its own merchant id in the body")
        void merchantMayStateOwnId() {
            assertThat(subscriptionService.createEndpoint(request(merchantA), callerA).merchantId()).isEqualTo(merchantA);
        }

        @Test
        @DisplayName("merchant cannot create an endpoint for another merchant")
        void merchantCannotCreateForAnotherMerchant() {
            assertThatThrownBy(() -> subscriptionService.createEndpoint(request(merchantB), callerA))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).save(any());
        }

        @Test
        @DisplayName("merchant without a merchant identity is rejected, never defaulted")
        void merchantWithoutIdentityRejected() {
            assertThatThrownBy(() -> subscriptionService.createEndpoint(request(null), merchant(null)))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> subscriptionService.createEndpoint(request(merchantA), merchant(null)))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).save(any());
        }

        @Test
        @DisplayName("system admin creates for the merchant it names")
        void adminCreatesForNamedMerchant() {
            ArgumentCaptor<WebhookEndpoint> saved = ArgumentCaptor.forClass(WebhookEndpoint.class);
            subscriptionService.createEndpoint(request(merchantB), admin);
            verify(endpointRepository).save(saved.capture());
            assertThat(saved.getValue().getMerchantId()).isEqualTo(merchantB);
        }

        @Test
        @DisplayName("system admin must name the merchant")
        void adminMustNameMerchant() {
            assertThatThrownBy(() -> subscriptionService.createEndpoint(request(null), admin))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(endpointRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = UserRole.class, names = {"DISPATCHER", "CUSTOMER", "COURIER"})
        @DisplayName("other roles cannot create endpoints")
        void otherRolesForbidden(UserRole role) {
            assertThatThrownBy(() -> subscriptionService.createEndpoint(request(merchantA), caller(role, merchantA)))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).save(any());
        }

        @Test
        @DisplayName("subscriptions are stored in canonical form")
        void canonicalisesSubscriptions() {
            WebhookEndpointResponse response = subscriptionService.createEndpoint(new CreateWebhookEndpointRequest(
                    null, URL, " order.created , parcel.*,order.created", null), callerA);
            assertThat(response.subscribedEvents()).isEqualTo("order.created,parcel.*");
        }

        @Test
        @DisplayName("malformed subscriptions are rejected without persisting")
        void rejectsInvalidSubscriptions() {
            assertThatThrownBy(() -> subscriptionService.createEndpoint(new CreateWebhookEndpointRequest(
                    null, URL, "parcel.status.changed,,parcel", null), callerA))
                    .isInstanceOf(InvalidEventSubscriptionException.class);
            verify(endpointRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("list")
    class ListEndpoints {

        @Test
        @DisplayName("merchant lists only its own endpoints")
        void merchantListsOwn() {
            when(endpointRepository.findByMerchantId(merchantA)).thenReturn(List.of(endpointOfA));

            assertThat(subscriptionService.listEndpoints(null, callerA))
                    .extracting(WebhookEndpointResponse::endpointId).containsExactly(endpointId);
            verify(endpointRepository).findByMerchantId(merchantA);
            verify(endpointRepository, never()).findAll();
        }

        @Test
        @DisplayName("merchant cannot list another merchant's endpoints")
        void merchantCannotListOtherMerchant() {
            assertThatThrownBy(() -> subscriptionService.listEndpoints(merchantA, callerB))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).findByMerchantId(any());
            verify(endpointRepository, never()).findAll();
        }

        @Test
        @DisplayName("merchant without identity never receives an unfiltered list")
        void merchantWithoutIdentityRejected() {
            assertThatThrownBy(() -> subscriptionService.listEndpoints(null, merchant(null)))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).findAll();
        }

        @Test
        @DisplayName("system admin may list all or filter by merchant")
        void adminListsAllOrFiltered() {
            when(endpointRepository.findAll()).thenReturn(List.of(endpointOfA));
            when(endpointRepository.findByMerchantId(merchantB)).thenReturn(List.of());

            assertThat(subscriptionService.listEndpoints(null, admin)).hasSize(1);
            assertThat(subscriptionService.listEndpoints(merchantB, admin)).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = UserRole.class, names = {"DISPATCHER", "CUSTOMER", "COURIER"})
        @DisplayName("other roles cannot list endpoints")
        void otherRolesForbidden(UserRole role) {
            assertThatThrownBy(() -> subscriptionService.listEndpoints(null, caller(role, null)))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).findAll();
        }
    }

    @Nested
    @DisplayName("single-endpoint operations")
    class SingleEndpoint {

        @Test
        @DisplayName("owner can get, toggle, ping, read attempts and delete")
        void ownerHasAccess() {
            assertThat(subscriptionService.getEndpoint(endpointId, callerA).endpointId()).isEqualTo(endpointId);

            assertThat(subscriptionService.toggleEndpointActive(endpointId, false, callerA).active()).isFalse();
            verify(endpointRepository).save(endpointOfA);

            WebhookDeliveryAttemptResponse ping = new WebhookDeliveryAttemptResponse(
                    UUID.randomUUID(), endpointId, merchantA, "webhook.test.ping", 200, 1, true, "OK", null, Instant.now());
            when(dispatcher.dispatch(eq(endpointOfA), eq("webhook.test.ping"), any())).thenReturn(ping);
            assertThat(subscriptionService.testPing(endpointId, callerA).success()).isTrue();

            WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
                    .attemptId(UUID.randomUUID()).endpointId(endpointId).merchantId(merchantA)
                    .eventType("parcel.status.changed").httpStatusCode(200).attemptNumber(1).success(true)
                    .attemptedAt(Instant.now()).build();
            when(attemptRepository.findByEndpointIdOrderByAttemptedAtDesc(endpointId)).thenReturn(List.of(attempt));
            assertThat(subscriptionService.getDeliveryAttempts(endpointId, callerA)).hasSize(1);

            subscriptionService.deleteEndpoint(endpointId, callerA);
            verify(endpointRepository).delete(endpointOfA);
        }

        @Test
        @DisplayName("another merchant gets the same not-found as for a missing endpoint, and nothing happens")
        void otherMerchantSeesNotFound() {
            UUID missingId = UUID.randomUUID();
            when(endpointRepository.findByEndpointId(missingId)).thenReturn(Optional.empty());
            String missingMessage = catchMessage(() -> subscriptionService.getEndpoint(missingId, callerB));

            List<Consumer<UUID>> operations = List.of(
                    id -> subscriptionService.getEndpoint(id, callerB),
                    id -> subscriptionService.toggleEndpointActive(id, false, callerB),
                    id -> subscriptionService.deleteEndpoint(id, callerB),
                    id -> subscriptionService.testPing(id, callerB),
                    id -> subscriptionService.getDeliveryAttempts(id, callerB));
            for (Consumer<UUID> operation : operations) {
                assertThatThrownBy(() -> operation.accept(endpointId))
                        .isInstanceOf(WebhookEndpointNotFoundException.class)
                        // Identical apart from the id the caller supplied.
                        .hasMessage(missingMessage.replace(missingId.toString(), endpointId.toString()));
            }

            assertThat(endpointOfA.isActive()).isTrue();
            verify(endpointRepository, never()).save(any());
            verify(endpointRepository, never()).delete(any());
            verifyNoInteractions(dispatcher, attemptRepository);
        }

        @Test
        @DisplayName("merchant without identity cannot reach any endpoint")
        void merchantWithoutIdentityForbidden() {
            assertThatThrownBy(() -> subscriptionService.getEndpoint(endpointId, merchant(null)))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> subscriptionService.deleteEndpoint(endpointId, merchant(null)))
                    .isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).delete(any());
        }

        @ParameterizedTest
        @EnumSource(value = UserRole.class, names = {"DISPATCHER", "CUSTOMER", "COURIER"})
        @DisplayName("other roles are refused before the endpoint is even looked up")
        void otherRolesForbidden(UserRole role) {
            CallerContext other = caller(role, merchantA);
            assertThatThrownBy(() -> subscriptionService.getEndpoint(endpointId, other)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> subscriptionService.toggleEndpointActive(endpointId, false, other)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> subscriptionService.deleteEndpoint(endpointId, other)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> subscriptionService.testPing(endpointId, other)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> subscriptionService.getDeliveryAttempts(endpointId, other)).isInstanceOf(ForbiddenException.class);
            verify(endpointRepository, never()).findByEndpointId(any());
            verifyNoInteractions(dispatcher, attemptRepository);
        }

        @Test
        @DisplayName("system admin can operate on any merchant's endpoint")
        void adminHasAccess() {
            assertThat(subscriptionService.getEndpoint(endpointId, admin).merchantId()).isEqualTo(merchantA);
            subscriptionService.toggleEndpointActive(endpointId, false, admin);
            subscriptionService.getDeliveryAttempts(endpointId, admin);
            subscriptionService.deleteEndpoint(endpointId, admin);
            verify(endpointRepository).delete(endpointOfA);
        }

        @Test
        @DisplayName("unknown endpoint is not found even for the system admin")
        void unknownEndpointNotFound() {
            UUID missingId = UUID.randomUUID();
            when(endpointRepository.findByEndpointId(missingId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> subscriptionService.getDeliveryAttempts(missingId, admin))
                    .isInstanceOf(WebhookEndpointNotFoundException.class);
            verifyNoInteractions(attemptRepository);
        }
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
        } catch (WebhookEndpointNotFoundException expected) {
            return expected.getMessage();
        }
        throw new AssertionError("Expected WebhookEndpointNotFoundException");
    }
}
