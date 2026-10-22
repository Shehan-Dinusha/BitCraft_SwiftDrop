package com.swiftdrop.iam.dto;

public record RegisterUserRequest(
        String username,
        String email,
        String password,
        String firstName,
        String lastName,
        String role
) {
}
