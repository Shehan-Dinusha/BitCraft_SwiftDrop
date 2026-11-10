package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "JSON Web Key Set. Empty in HS256 mode unless an RSA pair has been configured for migration.")
public record JwksResponse(@Schema(description = "Public RSA signing keys.") List<JwkResponse> keys) {
    public JwksResponse { keys = List.copyOf(keys); }
}
