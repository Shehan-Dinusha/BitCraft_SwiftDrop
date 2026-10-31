package com.swiftdrop.webhook.service;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private UUID merchantId;
    private DeadLetterWebhookRecord sampleRecord;
    private WebhookEndpoint sampleEndpoint;

    @BeforeEach
    void setUp() {
        dlqId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        merchantId = UUID.randomUUID();

        sampleRecord = DeadLetterWebhookRecord.builder()
                .dlqId(dlqId)
                .endpointId(endpointId)
                .merchantId(merchantId)
                .eventType("parcel.status.changed")
                .payload("{\"status\":\"DELIVERED\"}")
                .failureReason("Connection timed out")
                .totalAttempts(3)
                .replayed(false)
                .createdAt(Instant.now())
                .build();

        sampleEndpoint = WebhookEndpoint.builder()
                .endpointId(endpointId)
                .merchantId(merchantId)
                .targetUrl("https://merchant.example.com/webhooks")
                .secretKey("whsec_secret123")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("listDlqRecords should return all records when no filters")
    void testListDlqRecordsAll() {
        when(dlqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sampleRecord));

        List<DeadLetterWebhookResponse> result = dlqReplayService.listDlqRecords(null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dlqId()).isEqualTo(dlqId);
    }

    @Test
    @DisplayName("listDlqRecords should filter unreplayed records")
    void testListDlqRecordsUnreplayed() {
        when(dlqRepository.findByReplayedFalseOrderByCreatedAtDesc()).thenReturn(List.of(sampleRecord));

        List<DeadLetterWebhookResponse> result = dlqReplayService.listDlqRecords(null, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).replayed()).isFalse();
    }

    @Test
    @DisplayName("getDlqRecord should return DTO when found")
    void testGetDlqRecordFound() {
        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(sampleRecord));

        DeadLetterWebhookResponse response = dlqReplayService.getDlqRecord(dlqId);

        assertThat(response).isNotNull();
        assertThat(response.dlqId()).isEqualTo(dlqId);
    }

    @Test
    @DisplayName("getDlqRecord should throw when record not found")
    void testGetDlqRecordNotFound() {
        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dlqReplayService.getDlqRecord(dlqId))
                .isInstanceOf(DlqRecordNotFoundException.class)
                .hasMessageContaining("Dead-letter webhook record not found");
    }

    @Test
    @DisplayName("replayDlqRecord should succeed and mark record replayed when dispatch succeeds")
    void testReplayDlqRecordSuccess() {
        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(sampleRecord));
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));

        WebhookDeliveryAttemptResponse attemptResponse = new WebhookDeliveryAttemptResponse(
                UUID.randomUUID(), endpointId, merchantId, "parcel.status.changed", 200, 1, true, "OK", null, Instant.now()
        );
        when(dispatcher.dispatch(eq(sampleEndpoint), eq("parcel.status.changed"), any())).thenReturn(attemptResponse);

        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sampleRecord.isReplayed()).isTrue();
        assertThat(sampleRecord.getReplayedAt()).isNotNull();
        verify(dlqRepository).save(sampleRecord);
    }

    @Test
    @DisplayName("replayDlqRecord should fail when endpoint no longer exists")
    void testReplayDlqRecordEndpointMissing() {
        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(sampleRecord));
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.empty());

        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.statusCode()).isEqualTo(404);
        verify(dlqRepository, never()).save(sampleRecord);
    }

    @Test
    @DisplayName("replayDlqRecord should fail when endpoint is deactivated")
    void testReplayDlqRecordEndpointDeactivated() {
        sampleEndpoint.setActive(false);
        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(sampleRecord));
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));

        ReplayDlqResponse response = dlqReplayService.replayDlqRecord(dlqId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("replayAllUnreplayed should batch process unreplayed records")
    void testReplayAllUnreplayed() {
        when(dlqRepository.findByReplayedFalseOrderByCreatedAtDesc()).thenReturn(List.of(sampleRecord));
        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(sampleRecord));
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));

        WebhookDeliveryAttemptResponse attemptResponse = new WebhookDeliveryAttemptResponse(
                UUID.randomUUID(), endpointId, merchantId, "parcel.status.changed", 200, 1, true, "OK", null, Instant.now()
        );
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(attemptResponse);

        List<ReplayDlqResponse> results = dlqReplayService.replayAllUnreplayed(null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("SUCCESS");
    }
}
