package com.biashara.common.web;

import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.ForbiddenException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.common.exception.UnauthorisedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(NotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({BusinessRuleException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> onBadRequest(RuntimeException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({UnauthorisedException.class, BadCredentialsException.class})
    public ResponseEntity<ApiError> onUnauthorised(RuntimeException exception) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> onForbidden(RuntimeException exception) {
        String message = exception.getMessage() == null || exception instanceof AccessDeniedException
                ? "Your role does not have permission to perform this action"
                : exception.getMessage();
        return build(HttpStatus.FORBIDDEN, message);
    }

    /** Field-level validation failures, keyed by field so forms can highlight inputs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiError.validation(
                HttpStatus.BAD_REQUEST.value(), "Please correct the highlighted fields", fieldErrors));
    }

    /** Anything unmapped is a defect: log the stack trace, tell the caller nothing. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), message));
    }
}
