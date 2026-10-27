package com.swiftdrop.webhook.exception;

public class DlqRecordNotFoundException extends RuntimeException {
    public DlqRecordNotFoundException(String message) {
        super(message);
    }
}
