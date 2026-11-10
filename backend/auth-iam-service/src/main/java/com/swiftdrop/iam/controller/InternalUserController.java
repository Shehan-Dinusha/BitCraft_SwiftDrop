package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.UserContactResponse;
import com.swiftdrop.iam.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal service-to-service endpoints — NOT exposed through the public API gateway.
 *
 * <p><strong>Security contract:</strong> Every path under {@code /internal/**} is blocked
 * at the Spring Cloud Gateway level (Member 1's {@code GatewayRoutesConfig}) and may only
 * be called by other SwiftDrop backend services reaching the IAM service directly on its
 * internal cluster DNS ({@code auth-iam-service:8081}).
 *
 * <p>No JWT authentication is required because access is enforced at the network/gateway
 * boundary.  These endpoints must never be reachable from the public internet.
 *
 * <p>Current consumers:
 * <ul>
 *   <li>{@code notification-service} — resolves a user's email/phone before sending alerts.</li>
 *   <li>{@code webhook-resiliency-service} — retrieves merchant contact for failure notifications.</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/users")
@Tag(
    name  = "Internal – Users",
    description = "Internal service-to-service user data endpoints. "
                + "Must NOT be reachable from the public internet — blocked at gateway."
)
public class InternalUserController {

    private final UserAccountRepository userAccountRepository;

    public InternalUserController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Retrieve the email address and phone number of a user by their public UUID.
     *
     * <p>Intended for peer services that need to send a notification to a user.
     * Returns 404 if the user does not exist.
     *
     * <p><strong>PII exposure:</strong> this endpoint returns contact data.
     * It must only ever be called from trusted internal services.
     *
     * @param userId the user's public UUID (exposed in JWTs as {@code sub})
     * @return email and phone wrapped in the standard {@link ApiResponse} envelope
     */
    @GetMapping("/{userId}/contact")
    @Operation(
        summary = "Get a user's contact details (internal only)",
        description = "Returns the email address and phone number for the given user UUID. "
                    + "INTERNAL USE ONLY — blocked at the API gateway."
    )
    public ApiResponse<UserContactResponse> getContactDetails(@PathVariable UUID userId) {
        UserAccount account = userAccountRepository.findByPublicId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return ApiResponse.success(
                new UserContactResponse(account.getEmail(), account.getPhone()),
                "Contact details retrieved"
        );
    }
}

