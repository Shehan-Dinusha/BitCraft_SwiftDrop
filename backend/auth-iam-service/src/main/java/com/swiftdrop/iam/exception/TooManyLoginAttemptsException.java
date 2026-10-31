package com.swiftdrop.iam.exception;

import org.springframework.http.HttpStatus;

public class TooManyLoginAttemptsException extends AuthException {

    public TooManyLoginAttemptsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Try again later.");
    }
}
