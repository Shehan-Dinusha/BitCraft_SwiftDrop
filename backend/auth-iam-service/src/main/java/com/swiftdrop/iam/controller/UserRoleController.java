package com.swiftdrop.iam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "UserRole", description = "UserRole API operations")
public class UserRoleController {

    @GetMapping("/me")
    @Operation(summary = "GET /me")
    public String currentUser() {
        return "user";
    }
}
