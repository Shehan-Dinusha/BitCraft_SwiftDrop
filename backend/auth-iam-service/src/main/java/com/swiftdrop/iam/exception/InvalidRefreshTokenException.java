package com.swiftdrop.iam.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AuthException {

    public InvalidRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
