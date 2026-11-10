package com.swiftdrop.iam.controller;

import com.swiftdrop.iam.config.JwtKeyProvider;
import com.swiftdrop.iam.dto.JwksResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth/.well-known")
@Tag(name = "Signing keys", description = "Public RSA verification keys")
public class JwksController {
    private final JwtKeyProvider keys;

    public JwksController(JwtKeyProvider keys) { this.keys = keys; }

    @GetMapping("/jwks.json")
    @SecurityRequirements
    @Operation(summary = "Get the public JSON Web Key Set")
    public ResponseEntity<JwksResponse> jwks() {
        // Standard JWKS document deliberately uses its standard top-level keys member.
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(keys.jwks());
    }
}
