package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.notification.service.RecipientEmailStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "Stop these emails" link in a recipient's parcel email. Public: the recipient has no account, so the
 * random token from the link is the only proof. It stops that one parcel's recipient emails, nothing else.
 */
@RestController
@Tag(name = "RecipientEmails", description = "A parcel's recipient stops the emails about that parcel")
@RequiredArgsConstructor
public class RecipientEmailStopController {

    private final RecipientEmailStopService stopService;

    @PostMapping("/api/v1/notifications/email-stops")
    @SecurityRequirements
    @Operation(summary = "Stop the emails about one parcel to its recipient",
            description = "Public, no sign-in: the token from the email's link is the proof. Using it again succeeds. "
                    + "404 for an unknown token. The sender's own emails are not affected.")
    public ResponseEntity<ApiResponse<Void>> stop(@Valid @RequestBody StopEmailsRequest request) {
        if (!stopService.stop(request.token())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("This link is not valid. Copy the whole link from the email."));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "You will get no more emails about this parcel"));
    }

    @Schema(name = "StopEmailsRequest", description = "The token from the email's \"Stop these emails\" link")
    public record StopEmailsRequest(
            @NotBlank
            @Schema(description = "Token from the link", example = "q3kN0e6Zb2v8Yx1RjT5wL9mPa4sDc7Hf0uGi3KoBnEz")
            String token) {
    }
}
