package com.swiftdrop.iam.service;

import org.springframework.stereotype.Service;

@Service
public class TokenIssuanceService {

    public String issueAccessToken(String subject) {
        return "token-for-" + subject;
    }
}
