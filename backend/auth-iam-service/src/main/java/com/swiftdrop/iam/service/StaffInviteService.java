package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.events.auth.AuthStaffInviteEvent;
import com.swiftdrop.iam.config.OtpProperties;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.InvitePreviewResponse;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Email invites for staff accounts. A courier (created by their dispatcher) or a dispatcher (created by
 * an admin) never receives a password: they get a link and choose their own password.
 *
 * <p>Security properties:
 * <ul>
 *   <li>The token is 32 bytes from {@link SecureRandom}; only its SHA-256 hash is stored.</li>
 *   <li>The token travels in the URL fragment ({@code /invite#token=...}), which browsers never send
 *       to a server, so it stays out of access logs and Referer headers.</li>
 *   <li>Single use: accepting clears the hash. Sending a new invite replaces the old one.</li>
 *   <li>Expires after {@code swiftdrop.auth.invite.ttl-hours}; inactive accounts cannot accept.</li>
 * </ul>
 */
@Slf4j
@Service
public class StaffInviteService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final StaffInvitePublisher publisher;
    private final OtpProperties otpProperties;
    private final String linkBaseUrl;
    private final int ttlHours;

    public StaffInviteService(UserAccountRepository userAccountRepository,
                              PasswordEncoder passwordEncoder,
                              PasswordPolicy passwordPolicy,
                              StaffInvitePublisher publisher,
                              OtpProperties otpProperties,
                              @Value("${swiftdrop.auth.invite.link-base-url:http://localhost:5173}") String linkBaseUrl,
                              @Value("${swiftdrop.auth.invite.ttl-hours:72}") int ttlHours) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.publisher = publisher;
        this.otpProperties = otpProperties;
        this.linkBaseUrl = linkBaseUrl.endsWith("/") ? linkBaseUrl.substring(0, linkBaseUrl.length() - 1) : linkBaseUrl;
        this.ttlHours = ttlHours;
    }

    /** Generic failure for every wrong, used, or expired token, so nothing about the account leaks. */
    public static AuthException invalidInvite() {
        return new AuthException(HttpStatus.BAD_REQUEST,
                "This invite link is invalid or has expired. Ask for a new invite.");
    }

    /**
     * Gives the account a fresh invite (replacing any earlier one) and asks notification-service to
     * email the link once the surrounding transaction commits.
     */
    @Transactional
    public void sendInvite(UserAccount user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(HttpStatus.CONFLICT, "Reactivate the account before sending an invite");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new AuthException(HttpStatus.CONFLICT, "The account has no email address to send the invite to");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        user.setInviteTokenHash(sha256(token));
        user.setInviteExpiresAt(Instant.now().plus(Duration.ofHours(ttlHours)));
        userAccountRepository.save(user);

        String inviteUrl = linkBaseUrl + "/invite#token=" + token;
        publisher.publish(new AuthStaffInviteEvent(null, null, user.getPublicId(), user.getRole(),
                inviteUrl, ttlHours, null));
        if (otpProperties.isLogCode()) {
            log.warn("[DEV ONLY] Invite link for user {}: {}", user.getPublicId(), inviteUrl);
        }
    }

    /** Re-sends the invite of an account that has not accepted yet. */
    @Transactional
    public void resendInvite(UserAccount user) {
        if (!user.isInvitePending()) {
            throw new AuthException(HttpStatus.CONFLICT, "This account has already been set up");
        }
        sendInvite(user);
    }

    /** What the invite page shows before the person chooses a password. */
    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String token) {
        UserAccount user = requirePending(token);
        return new InvitePreviewResponse(user.getFirstName(), user.getUsername(), user.getRole(),
                user.getDistrict(), user.getInviteExpiresAt());
    }

    /**
     * Sets the password chosen on the invite page and spends the invite. Holding the emailed link
     * proves control of the mailbox, so the caller is then signed in.
     */
    @Transactional
    public UserAccount accept(String token, String password) {
        // Generic rules first: they reveal nothing about the account.
        passwordPolicy.validate(password);
        UserAccount user = requirePending(token);
        passwordPolicy.validate(password, user.getUsername(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        user.clearInvite();
        return userAccountRepository.save(user);
    }

    private UserAccount requirePending(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw invalidInvite();
        }
        return userAccountRepository.findByInviteTokenHash(sha256(token))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getInviteExpiresAt() != null && user.getInviteExpiresAt().isAfter(Instant.now()))
                .orElseThrow(StaffInviteService::invalidInvite);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
