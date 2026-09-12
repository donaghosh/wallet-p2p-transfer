package com.paytm.wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.wallet.constants.ErrorCode;
import com.paytm.wallet.dto.ErrorResponse;
import com.paytm.wallet.util.CurrentUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the {@code Authorization: Bearer <token>} header to a user id and exposes it
 * via {@link CurrentUserContext}. Unknown/missing tokens get a clean 401 in the standard
 * error contract. Actuator endpoints are unauthenticated so health/metrics stay probeable.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(request, response, "Missing or malformed bearer token");
            return;
        }
        String userId = authProperties.resolveUser(header.substring(BEARER_PREFIX.length()).trim());
        if (userId == null) {
            writeUnauthorized(request, response, "Invalid bearer token");
            return;
        }
        try {
            CurrentUserContext.set(userId);
            chain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private void writeUnauthorized(
            HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                ErrorCode.UNAUTHORIZED.httpStatus().value(),
                ErrorCode.UNAUTHORIZED.name(),
                message,
                request.getRequestURI(),
                MDC.get("traceId"),
                null);
        response.setStatus(ErrorCode.UNAUTHORIZED.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
