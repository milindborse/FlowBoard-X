package com.flowboardx.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboardx.web.response.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without this, Spring Security's default behavior (no formLogin/httpBasic configured)
 * returns 403 for ANY unauthenticated request — including simply "missing or expired token" —
 * which is misleading. This makes unauthenticated requests correctly return 401 with a
 * proper ApiError body, so the frontend's existing 401-interceptor (client.ts) actually fires
 * and redirects to /login instead of silently failing with a 403.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                "Authentication required. Please log in again.",
                request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}