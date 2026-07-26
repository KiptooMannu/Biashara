package com.biashara.common.exception;

/**
 * Thrown when a request is well-formed but would violate a domain rule — selling
 * more stock than exists, say, or reusing an email. Mapped to HTTP 400 with the
 * message shown to the user, so these messages are written for humans.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
