package com.example.template.shared.security;

import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes RFC 9457 responses from security filters, which answer before MVC and therefore
 * bypass {@code GlobalExceptionHandler}.
 */
@Component
public class ProblemDetailResponseWriter {

    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public ProblemDetailResponseWriter(ProblemDetailFactory problemDetailFactory, ObjectMapper objectMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        var problem = problemDetailFactory.create(code);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
