package com.swiftdrop.iam.exception;

import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends AuthException {

    public AccountNotActiveException() {
        super(HttpStatus.FORBIDDEN, "Account is not active");
    }
}
