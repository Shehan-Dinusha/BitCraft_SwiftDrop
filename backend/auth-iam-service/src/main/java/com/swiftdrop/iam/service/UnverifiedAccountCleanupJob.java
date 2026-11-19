package com.swiftdrop.iam.service;

import com.swiftdrop.iam.config.OtpProperties;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes self-service sign-ups that never confirmed their email, so abandoned registrations do
 * not hold usernames or addresses forever. Safe to run on several instances: the delete is idempotent.
 */
@Slf4j
@Component
public class UnverifiedAccountCleanupJob {

    private final UserAccountRepository userAccountRepository;
    private final OtpProperties props;

    public UnverifiedAccountCleanupJob(UserAccountRepository userAccountRepository, OtpProperties props) {
        this.userAccountRepository = userAccountRepository;
        this.props = props;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeAbandonedSignUps() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(props.getUnverifiedRetentionHours()));
        int removed = userAccountRepository.deleteUnverifiedCreatedBefore(cutoff);
        if (removed > 0) {
            log.info("Removed {} unverified sign-up(s) older than {}h", removed, props.getUnverifiedRetentionHours());
        }
    }
}
