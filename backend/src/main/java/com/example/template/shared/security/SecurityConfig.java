package com.example.template.shared.security;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless and explicit: Security 7 lambda DSL only, no {@code JSESSIONID} ever, every route
 * explicitly authorized (the default is authenticated, never an implicit permitAll). The only
 * anonymous endpoints are the auth entry points and {@code /actuator/health} — plus springdoc
 * where it is enabled (dev/test only).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppSecurityProperties properties;
    private final boolean springdocEnabled;

    public SecurityConfig(
            AppSecurityProperties properties, @Value("${springdoc.api-docs.enabled:true}") boolean springdocEnabled) {
        this.properties = properties;
        this.springdocEnabled = springdocEnabled;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, ProblemDetailResponseWriter problemWriter) {
        http.csrf(AbstractHttpConfigurer::disable) // bearer-token API; the cookie-authenticated
                // auth endpoints get their own origin check in AuthOriginValidationFilter
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        // JSON API: nothing may load or frame it. Swagger UI (dev/test only)
                        // needs same-origin scripts and its inline styles.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(springdocEnabled
                                ? "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;"
                                        + " frame-ancestors 'none'"
                                : "default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(HttpMethod.POST, "/api/v*/auth/login", "/api/v*/auth/2fa/verify")
                            .permitAll()
                            // refresh and logout authenticate by cookie alone
                            .requestMatchers(HttpMethod.POST, "/api/v*/auth/refresh", "/api/v*/auth/logout")
                            .permitAll()
                            .requestMatchers("/actuator/health")
                            .permitAll();
                    if (springdocEnabled) {
                        authorize
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                (_, response, _) -> problemWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((_, response, _) -> problemWriter.write(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new AuthOriginValidationFilter(properties, problemWriter), JwtAuthenticationFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(properties, problemWriter), AuthOriginValidationFilter.class);
        return http.build();
    }

    /**
     * Central CORS config from the explicit origin allowlist; {@code allowCredentials=true},
     * therefore no wildcard origins — the properties reject them at startup. The browser
     * frontends call with {@code credentials: 'include'}; a CORS change is an API contract
     * change.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * DelegatingPasswordEncoder so the algorithm can rotate without a migration — the auth
     * service re-encodes on successful login when the stored hash is below current parameters.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        var encoders = Map.<String, PasswordEncoder>of(
                "bcrypt",
                new BCryptPasswordEncoder(12),
                "argon2",
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }
}
