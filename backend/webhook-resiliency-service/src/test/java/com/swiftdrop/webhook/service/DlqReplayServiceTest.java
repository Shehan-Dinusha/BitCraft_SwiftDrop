package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DlqReplayServiceTest {

    private static final String CALLBACK_HOST = "shop-a.internal.example";
    private static final String CALLBACK_URL = "https://" + CALLBACK_HOST + ":8443/hooks/swiftdrop-7f3a";
    /** What the retired dispatcher stored: the exception message, including the merchant's callback URL. */
    private static final String RAW_FAILURE = "503 Service Unavailable from POST " + CALLBACK_URL;
    private static final String PAYLOAD = "{\"status\":\"DELIVERED\",\"recipientName\":\"Nimal Perera\"}";

    @Mock
    private DeadLetterWebhookRecordRepository dlqRepository;

    @InjectMocks
    private DlqReplayService dlqReplayService;

    private UUID dlqId;
    private UUID endpointId;
    private UUID merchantA;
    private UUID merchantB;
    private DeadLetterWebhookRecord recordOfA;
    private CallerContext callerA;
    private CallerContext callerB;
    private CallerContext admin;

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
                .payload(PAYLOAD)
                .failureReason(RAW_FAILURE)
                .totalAttempts(3)
                .replayed(false)
                .createdAt(Instant.now())
                .build();

        callerA = caller(UserRole.MERCHANT, merchantA);
        callerB = caller(UserRole.MERCHANT, merchantB);
        admin = caller(UserRole.SYSTEM_ADMIN, null);

        when(dlqRepository.findByDlqId(dlqId)).thenReturn(Optional.of(recordOfA));
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
    @DisplayName("owner and system admin keep the full record, plus the sanitised fields")
    void ownerAndAdminKeepFullDetail() {
        when(dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantA)).thenReturn(List.of(recordOfA));
        when(dlqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(recordOfA));

        List<DeadLetterWebhookResponse> views = List.of(
                dlqReplayService.getDlqRecord(dlqId, callerA),
                dlqReplayService.getDlqRecord(dlqId, admin),
                dlqReplayService.listDlqRecords(null, false, callerA).getFirst(),
                dlqReplayService.listDlqRecords(null, false, admin).getFirst());
        for (DeadLetterWebhookResponse view : views) {
            assertThat(view.payload()).isEqualTo(PAYLOAD);
            assertThat(view.endpointId()).isEqualTo(endpointId);
            assertThat(view.failureReason()).isEqualTo(RAW_FAILURE);
            assertThat(view.errorClass()).isEqualTo(AttemptErrorClass.HTTP_SERVER_ERROR);
            assertThat(view.failureSummary()).isEqualTo("Endpoint returned a server error (HTTP 503)");
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "COURIER", "DISPATCHER"})
    @DisplayName("customers, couriers and dispatchers have no access to the legacy records")
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
    @DisplayName("owner and admin see the full record")
    void getRecordByRole() {
        assertThat(dlqReplayService.getDlqRecord(dlqId, callerA).payload()).isEqualTo(recordOfA.getPayload());
        assertThat(dlqReplayService.getDlqRecord(dlqId, admin).payload()).isEqualTo(recordOfA.getPayload());
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

    // --- retired replay (Step 17) ------------------------------------------------------------------------------

    @Test
    @DisplayName("the service can no longer replay or send: its only dependency is the legacy record repository")
    void legacyServiceCannotSend() {
        assertThat(Arrays.stream(DlqReplayService.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getType))
                .containsExactly(DeadLetterWebhookRecordRepository.class);
        assertThat(Arrays.stream(DlqReplayService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).map(Method::getName))
                .containsExactlyInAnyOrder("listDlqRecords", "getDlqRecord");
    }

    @Test
    @DisplayName("listing and reading never write a legacy record")
    void readsNeverWrite() {
        when(dlqRepository.findByMerchantIdOrderByCreatedAtDesc(merchantA)).thenReturn(List.of(recordOfA));
        when(dlqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(recordOfA));

        dlqReplayService.listDlqRecords(null, false, callerA);
        dlqReplayService.listDlqRecords(null, true, admin);
        dlqReplayService.getDlqRecord(dlqId, callerA);

        verify(dlqRepository, never()).save(any());
        verify(dlqRepository, never()).saveAll(any());
        verify(dlqRepository, never()).delete(any());
        assertThat(recordOfA.isReplayed()).isFalse();
    }
}
