package com.swiftdrop.iam.service;

import org.springframework.stereotype.Service;

@Service
public class AuthIamService {

    public String getStatus() {
        return "ready";
    }
}
