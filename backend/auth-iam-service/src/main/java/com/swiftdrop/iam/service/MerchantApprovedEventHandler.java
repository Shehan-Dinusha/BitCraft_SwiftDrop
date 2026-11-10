package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.merchant.MerchantApprovedEvent;
import com.swiftdrop.iam.config.RabbitMQConfig;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * RabbitMQ consumer for {@code merchant.approved} events published by {@code merchant-b2b-service}.
 *
 * <p>When a merchant account is approved, this handler:
 * <ol>
 *   <li>Guards against duplicate delivery using the {@code processed_merchant_events} table.</li>
 *   <li>Looks up the target {@link UserAccount} by the event's {@code ownerUserId} field.</li>
 *   <li>Sets {@code UserAccount.merchantId} so subsequent JWT tokens carry a {@code merchantId} claim.</li>
 * </ol>
 *
 * <p><strong>Idempotency:</strong> An {@code INSERT} into {@code processed_merchant_events} is attempted
 * first. If the row already exists (duplicate {@code event_id}) a {@link DuplicateKeyException} is
 * caught and the message is silently acknowledged, preventing any state mutation.
 *
 * <p>Routing key: {@link EventRoutingKeys#MERCHANT_APPROVED}<br>
 * Queue: {@link RabbitMQConfig#MERCHANT_EVENTS_QUEUE}
 */
@Service
public class MerchantApprovedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MerchantApprovedEventHandler.class);

    private final UserAccountRepository userAccountRepository;
    private final JdbcTemplate          jdbc;

    public MerchantApprovedEventHandler(UserAccountRepository userAccountRepository,
                                        JdbcTemplate          jdbc) {
        this.userAccountRepository = userAccountRepository;
        this.jdbc                  = jdbc;
    }

    /**
     * Process a {@link MerchantApprovedEvent}.
     *
     * <p>Spring AMQP deserialises the JSON body into a {@code MerchantApprovedEvent} record using the
     * Jackson2JsonMessageConverter configured in Spring Boot auto-configuration.
     *
     * <p>The method is {@link Transactional} so the idempotency insert and the merchant-id update
     * are committed atomically. If any step throws, the whole transaction rolls back and the message
     * is requeued for retry.
     *
     * @param event the deserialized merchant-approval event
     */
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.MERCHANT_EVENTS_QUEUE)
    public void onMerchantApproved(MerchantApprovedEvent event) {
        log.info("Received merchant.approved event: eventId={} merchantId={} ownerUserId={}",
                event.eventId(), event.merchantId(), event.ownerUserId());

        // ── Idempotency guard ─────────────────────────────────────────────────
        try {
            jdbc.update(
                "INSERT INTO processed_merchant_events (event_id, merchant_id, user_id) VALUES (?, ?, ?)",
                event.eventId(), event.merchantId(), event.ownerUserId()
            );
        } catch (DuplicateKeyException duplicate) {
            // Already processed — safe to discard.
            log.info("Duplicate merchant.approved event {} — skipping", event.eventId());
            return;
        }

        // ── Link merchant ID to the IAM user account ──────────────────────────
        Optional<UserAccount> accountOpt = userAccountRepository.findByPublicId(event.ownerUserId());
        if (accountOpt.isEmpty()) {
            // The merchant-b2b-service may have created the user before IAM replicated it,
            // or the ownerUserId is incorrect. Log and do NOT requeue (would loop forever).
            log.warn("merchant.approved for unknown ownerUserId={} — event acknowledged without update",
                    event.ownerUserId());
            return;
        }

        UserAccount account = accountOpt.get();
        if (account.getMerchantId() != null && !account.getMerchantId().equals(event.merchantId())) {
            log.warn("ownerUserId={} already has merchantId={} — overwriting with merchantId={} from event={}",
                    event.ownerUserId(), account.getMerchantId(), event.merchantId(), event.eventId());
        }

        account.setMerchantId(event.merchantId());
        userAccountRepository.save(account);

        log.info("Linked merchantId={} to ownerUserId={} from event={}", event.merchantId(), event.ownerUserId(), event.eventId());
    }
}
