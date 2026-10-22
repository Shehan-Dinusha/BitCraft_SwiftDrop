package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Proof-of-delivery evidence submitted for a delivery.")
public record ProofOfDeliveryUploadRequest(
        @Schema(description = "Object-storage URL for the delivery photo.", example = "https://storage.example/pod/123.jpg")
        String photoUrl,
        @Schema(description = "Recipient signature encoded as a portable string.", example = "base64-signature")
        String recipientSignature,
        @Schema(description = "One-time confirmation code supplied by the recipient.", example = "482913")
        String confirmationOtp
) {
}
