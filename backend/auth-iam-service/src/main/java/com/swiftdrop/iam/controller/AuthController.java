package com.swiftdrop.iam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.swiftdrop.iam.dto.LoginRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Auth API operations")
public class AuthController {

    @PostMapping("/login")
    @Operation(summary = "POST /login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        return new TokenResponse("access-token", "refresh-token", "Bearer", 3600L);
    }
}
