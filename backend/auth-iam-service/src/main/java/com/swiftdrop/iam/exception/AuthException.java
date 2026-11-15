package com.swiftdrop.iam.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private final HttpStatus status;
    /** Optional machine-readable code the frontend can branch on (null for plain errors). */
    private final String code;

    public AuthException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public AuthException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
