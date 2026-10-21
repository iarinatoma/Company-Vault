package com.example.template.shared.error;

import com.example.template.shared.security.RefreshTokenCookieFactory;
import jakarta.persistence.OptimisticLockException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Translates every failure into an RFC 9457 {@code ProblemDetail} with a stable {@code code}
 * and the current {@code traceId}. Controllers have no try/catch — everything lands here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public GlobalExceptionHandler(
            ProblemDetailFactory problemDetailFactory, RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.problemDetailFactory = problemDetailFactory;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        var problem = problemDetailFactory.create(exception.errorCode(), exception.getMessage());
        var response = ResponseEntity.status(exception.errorCode().status());
        if (exception.errorCode() == ErrorCode.INVALID_REFRESH_TOKEN) {
            // A failed refresh clears the cookie.
            response.header(
                    HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString());
        }
        return response.body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        var problem = problemDetailFactory.create(ErrorCode.VALIDATION_FAILED);
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            // A constraint without a message is possible in theory; the field name still helps.
            errors.putIfAbsent(
                    fieldError.getField(), Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Invalid value"));
        }
        problem.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleHandlerMethodValidation() {
        var problem = problemDetailFactory.create(ErrorCode.VALIDATION_FAILED);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(problem);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleOptimisticLock() {
        // The client's cue to reload and retry — never retried server-side.
        var problem = problemDetailFactory.create(ErrorCode.CONCURRENT_MODIFICATION);
        return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.status()).body(problem);
    }

    @ExceptionHandler({InvalidApiVersionException.class, MissingApiVersionException.class})
    ResponseEntity<ProblemDetail> handleApiVersion() {
        // An unsupported or malformed version fails deterministically, never a silent 404.
        var problem = problemDetailFactory.create(ErrorCode.UNSUPPORTED_API_VERSION);
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_API_VERSION.status()).body(problem);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ProblemDetail> handleAuthorizationDenied() {
        var problem = problemDetailFactory.create(ErrorCode.FORBIDDEN);
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        var problem = problemDetailFactory.create(ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(problem);
    }
}
