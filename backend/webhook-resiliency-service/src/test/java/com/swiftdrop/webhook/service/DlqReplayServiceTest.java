package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.ReplayDlqResponse;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.exception.DlqRecordNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DlqReplayServiceTest {

    @Mock
    private DeadLetterWebhookRecordRepository dlqRepository;

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private OutboundWebhookDispatcher dispatcher;

    @InjectMocks
    private DlqReplayService dlqReplayService;

    private UUID dlqId;
    private UUID endpointId;
    private UUID merchantA;
    private UUID merchantB;
    private DeadLetterWebhookRecord recordOfA;
    private WebhookEndpoint endpointOfA;
    private CallerContext callerA;
    private CallerContext callerB;
    private CallerContext admin;
    private CallerContext dispatcherCaller;

    @BeforeEach
    void setUp() {
        dlqId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        merchantA = UUID.randomUUID();
        merchantB = UUID.randomUUID();

        recordOfA = DeadLetterWebhookRecord.builder()
                .dlqId(dlqId)
                .endpointId(endpointId)
                .merchantId(merchantA)
                .eventType("parcel.status.changed")
                .payload("{\"status\":\"DELIVERED\"}")
                .failureReason("Connection timed out")
                .totalAttempts(3)
                .replayed(false)
                .createdAt(Instant.now())
                .build();

        endpointOfA = WebhookEndpoint.builder()
                .endpointId(endpointId)
                .merchantId(merchantA)
                .targetUrl("https://merchant.example.com/webhooks")
                .secretKey("whsec_secret123")
                .active(true)
                .build();

        callerA = caller(UserRole.MERCHANT, merchantA);
        callerB = caller(UserRole.MERCHANT, merchantB);
        admin = caller(UserRole.SYSTEM_ADMIN, null);
        dispatcherCaller = caller(UserRole.DISPATCHER, null);

        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(recordOfA));
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(endpointOfA));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(new WebhookDeliveryAttemptResponse(
                UUID.randomUUID(), endpointId, merchantA, "parcel.status.changed", 200, 1, true, "OK", null, Instant.now()));
    }

    private static CallerContext caller(UserRole role, UUID merchantId) {
        return new CallerContext(UUID.randomUUID(), role, merchantId, null);
    }

    // --- list -----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("merchant lists only its own records, never the unfiltered set")
    void merchantListsOwn() {
        when(dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantA)).thenReturn(List.of(recordOfA));

        List<DeadLetterWebhookResponse> result = dlqReplayService.listDlqRecords(null, false, callerA);

        assertThat(result).singleElement().satisfies(r -> assertThat(r.payload()).isNotNull());
        verify(dlqRepository, never()).findAllByOrderByCreatedAtDesc();
        verify(dlqRepository, never()).findByReplayedFalseOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("merchant cannot list another merchant's records")
    void merchantCannotListOther() {
        assertThatThrownBy(() -> dlqReplayService.listDlqRecords(merchantA, false, callerB))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(dispatcher);
        verify(dlqRepository, never()).findByMerchantIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("unreplayed filter is applied within the merchant's own records")
    void merchantUnreplayedFilter() {
        DeadLetterWebhookRecord replayed = DeadLetterWebhookRecord.builder()
                .dlqId(UUID.randomUUID()).endpointId(endpointId).merchantId(merchantA).eventType("x.y")
                .payload("{}").replayed(true).createdAt(Instant.now()).build();
        when(dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantA)).thenReturn(List.of(recordOfA, replayed));

        assertThat(dlqReplayService.listDlqRecords(null, true, callerA))
                .extracting(DeadLetterWebhookResponse::dlqId).containsExactly(dlqId);
    }

    @Test
    @DisplayName("system admin lists all records, or one merchant's, including payloads")
    void adminListsAll() {
        when(dlqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(recordOfA));
        when(dlqRepository.findByReplayedFalseOrderByCreatedAtDesc()).thenReturn(List.of(recordOfA));
        when(dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantB)).thenReturn(List.of());

        assertThat(dlqReplayService.listDlqRecords(null, false, admin))
                .singleElement().satisfies(r -> assertThat(r.payload()).isEqualTo(recordOfA.getPayload()));
        assertThat(dlqReplayService.listDlqRecords(null, true, admin)).hasSize(1);
        assertThat(dlqReplayService.listDlqRecords(merchantB, false, admin)).isEmpty();
    }

    @Test
    @DisplayName("dispatcher lists across merchants but receives metadata without the payload")
    void dispatcherListsMetadataOnly() {
        when(dlqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(recordOfA));

        assertThat(dlqReplayService.listDlqRecords(null, false, dispatcherCaller)).singleElement().satisfies(r -> {
            assertThat(r.payload()).isNull();
            assertThat(r.dlqId()).isEqualTo(dlqId);
            assertThat(r.merchantId()).isEqualTo(merchantA);
            assertThat(r.eventType()).isEqualTo("parcel.status.changed");
            assertThat(r.failureReason()).isEqualTo("Connection timed out");
        });
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "COURIER"})
    @DisplayName("customers and couriers have no DLQ access")
    void otherRolesCannotList(UserRole role) {
        assertThatThrownBy(() -> dlqReplayService.listDlqRecords(null, false, caller(role, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> dlqReplayService.getDlqRecord(dlqId, caller(role, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(dlqRepository, never()).findAllByOrderByCreatedAtDesc();
        verify(dlqRepository, never()).findByDlqId(any());
    }

    @Test
    @DisplayName("merchant without merchant identity is rejected")
    void merchantWithoutIdentity() {
        assertThatThrownBy(() -> dlqReplayService.listDlqRecords(null, false, caller(UserRole.MERCHANT, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(dlqRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    // --- get ------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("owner and admin see the full record; dispatcher sees it without the payload")
    void getRecordByRole() {
        assertThat(dlqReplayService.getDlqRecord(dlqId, callerA).payload()).isEqualTo(recordOfA.getPayload());
        assertThat(dlqReplayService.getDlqRecord(dlqId, admin).payload()).isEqualTo(recordOfA.getPayload());
        assertThat(dlqReplayService.getDlqRecord(dlqId, dispatcherCaller).payload()).isNull();
    }

    @Test
    @DisplayName("another merchant's record is indistinguishable from a missing one")
    void otherMerchantRecordNotFound() {
        UUID missing = UUID.randomUUID();
        when(dlqRepository.findByDlqId(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dlqReplayService.getDlqRecord(missing, callerB))
                .isInstanceOf(DlqRecordNotFoundException.class)
                .hasMessage("Dead-letter webhook record not found with ID: " + missing);
        assertThatThrownBy(() -> dlqReplayService.getDlqRecord(dlqId, callerB))
                .isInstanceOf(DlqRecordNotFoundException.class)
                .hasMessage("Dead-letter webhook record not found with ID: " + dlqId);
    }

    // --- replay ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("owner replays its own record and it is marked replayed")
    void ownerReplays() {
        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId, callerA);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(recordOfA.isReplayed()).isTrue();
        assertThat(recordOfA.getReplayedAt()).isNotNull();
        verify(dlqRepository).save(recordOfA);
    }

    @Test
    @DisplayName("another merchant cannot replay the record: not found and nothing dispatched")
    void otherMerchantCannotReplay() {
        assertThatThrownBy(() -> dlqReplayService.replayDlqRecord(dlqId, callerB))
                .isInstanceOf(DlqRecordNotFoundException.class);
        verifyNoInteractions(dispatcher);
        assertThat(recordOfA.isReplayed()).isFalse();
    }

    @Test
    @DisplayName("dispatcher cannot replay a record or replay all")
    void dispatcherCannotReplay() {
        assertThatThrownBy(() -> dlqReplayService.replayDlqRecord(dlqId, dispatcherCaller))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> dlqReplayService.replayAllUnreplayed(null, dispatcherCaller))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("system admin can replay any merchant's record")
    void adminReplays() {
        assertThat(dlqReplayService.replayDlqRecord(dlqId, admin).status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("replay fails cleanly when the endpoint no longer exists")
    void replayEndpointMissing() {
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.empty());

        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId, callerA);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.statusCode()).isEqualTo(404);
        verify(dlqRepository, never()).save(recordOfA);
    }

    @Test
    @DisplayName("replay fails cleanly when the endpoint is deactivated")
    void replayEndpointDeactivated() {
        endpointOfA.setActive(false);

        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId, callerA);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.statusCode()).isEqualTo(400);
    }

    // --- replay all -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("merchant replay-all is limited to its own unreplayed records")
    void merchantReplayAllOwn() {
        when(dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantA)).thenReturn(List.of(recordOfA));

        assertThat(dlqReplayService.replayAllUnreplayed(null, callerA)).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo("SUCCESS"));
        verify(dlqRepository, never()).findByReplayedFalseOrderByCreatedAtDesc();
        verify(dispatcher).dispatch(eq(endpointOfA), eq("parcel.status.changed"), any());
    }

    @Test
    @DisplayName("merchant cannot replay-all another merchant's records")
    void merchantCannotReplayAllOther() {
        assertThatThrownBy(() -> dlqReplayService.replayAllUnreplayed(merchantA, callerB))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("system admin replay-all covers every merchant when no filter is given")
    void adminReplayAll() {
        when(dlqRepository.findByReplayedFalseOrderByCreatedAtDesc()).thenReturn(List.of(recordOfA));

        assertThat(dlqReplayService.replayAllUnreplayed(null, admin)).hasSize(1);
    }
}
