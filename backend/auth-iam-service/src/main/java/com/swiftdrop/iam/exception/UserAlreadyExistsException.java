package com.swiftdrop.iam.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends AuthException {

    public UserAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
