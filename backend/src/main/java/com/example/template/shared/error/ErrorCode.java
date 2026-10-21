package com.example.template.shared.error;

import org.springframework.http.HttpStatus;

/**
 * The closed set of business errors. Clients and tests key on the stable code and status,
 * never on message text.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    UNSUPPORTED_API_VERSION(HttpStatus.BAD_REQUEST, "Unsupported or malformed API version"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Request origin is not allowed"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "Email is already in use"),
    CANNOT_MODIFY_OWN_ACCOUNT(HttpStatus.CONFLICT, "You cannot change your own role or delete your own account"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was modified concurrently — reload and retry"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
