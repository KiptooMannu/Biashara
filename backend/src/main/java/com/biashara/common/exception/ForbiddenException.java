package com.biashara.common.exception;

/**
 * Thrown when an authenticated caller is not allowed to perform an action —
 * typically a hierarchy violation, such as a manager trying to create a user
 * senior to themselves. Mapped to HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
