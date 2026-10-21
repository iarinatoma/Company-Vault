package com.example.template.shared.security;

import com.example.template.shared.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Builds {@link AuthPrincipal} from the access token with no DB lookup. An invalid, expired
 * or wrong-{@code typ} token simply leaves the request anonymous — the authorization rules
 * and entry point then answer 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                var claims = jwtService.parse(header.substring(BEARER_PREFIX.length()), TokenType.ACCESS);
                if (claims.email() != null && claims.role() != null) {
                    var principal = new AuthPrincipal(claims.userId(), claims.email(), claims.role());
                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ApiException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
