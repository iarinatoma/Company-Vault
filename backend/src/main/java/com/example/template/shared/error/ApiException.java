package com.example.template.shared.error;

/**
 * The only exception services throw for business failures. Never {@code ResponseStatusException}
 * or raw {@code RuntimeException} — {@link GlobalExceptionHandler} translates this into an
 * RFC 9457 {@code ProblemDetail}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
