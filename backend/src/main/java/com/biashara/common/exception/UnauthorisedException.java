package com.biashara.common.exception;

/** Thrown when a request carries no valid identity. Mapped to HTTP 401. */
public class UnauthorisedException extends RuntimeException {

    public UnauthorisedException(String message) {
        super(message);
    }
}
