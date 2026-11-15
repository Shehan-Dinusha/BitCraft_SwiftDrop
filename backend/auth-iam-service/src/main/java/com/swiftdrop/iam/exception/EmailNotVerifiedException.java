package com.swiftdrop.iam.exception;

import org.springframework.http.HttpStatus;

/** Credentials were correct, but the account's email address has not been verified yet. */
public class EmailNotVerifiedException extends AuthException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN, "Email address is not verified. Enter the code we sent to your email.", CODE);
    }
}
