package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "RSA public verification key. Contains no private or symmetric key material.")
public record JwkResponse(
        @Schema(description = "Key type.", allowableValues = "RSA") String kty,
        @Schema(description = "Public key use.", allowableValues = "sig") String use,
        @Schema(description = "Allowed signature algorithm.", allowableValues = "RS256") String alg,
        @Schema(description = "Stable SHA-256 fingerprint of the X.509 public key, encoded as base64url.") String kid,
        @Schema(description = "Unsigned RSA modulus encoded as base64url without padding.") String n,
        @Schema(description = "Unsigned RSA exponent encoded as base64url without padding.") String e
) {
}
