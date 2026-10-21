package com.example.template.shared.error;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds RFC 9457 {@link ProblemDetail} bodies. Every problem carries the stable {@code code}
 * and the current {@code traceId} so support can triage on traceId + code, never on message text.
 * Shared by {@link GlobalExceptionHandler} and the security filters that answer before MVC.
 */
@Component
public class ProblemDetailFactory {

    private final ObjectProvider<Tracer> tracer;

    public ProblemDetailFactory(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    public ProblemDetail create(ErrorCode code) {
        return create(code, code.defaultMessage());
    }

    public ProblemDetail create(ErrorCode code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.status().getReasonPhrase());
        problem.setProperty("code", code.name());
        var currentTracer = tracer.getIfAvailable();
        if (currentTracer != null) {
            Span span = currentTracer.currentSpan();
            if (span != null) {
                problem.setProperty("traceId", span.context().traceId());
            }
        }
        return problem;
    }
}
