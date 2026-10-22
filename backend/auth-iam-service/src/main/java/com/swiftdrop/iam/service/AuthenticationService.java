package com.swiftdrop.iam.service;

import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    public boolean authenticate(String username, String password) {
        return username != null && password != null && !username.isBlank() && !password.isBlank();
    }
}
