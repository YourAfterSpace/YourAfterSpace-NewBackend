package com.yourafterspace.yas_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security configuration filter.
 *
 * <p>Adds security headers to all responses to protect against common vulnerabilities. This filter
 * runs after request ID and authentication filters.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1) // Run after other filters but before response is sent
public class SecurityConfig extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Prevent clickjacking
    response.setHeader("X-Frame-Options", "DENY");

    // Prevent MIME type sniffing
    response.setHeader("X-Content-Type-Options", "nosniff");

    // Enable XSS protection
    response.setHeader("X-XSS-Protection", "1; mode=block");

    // Strict Transport Security (HSTS) - only if using HTTPS
    if (request.isSecure()) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    // Content Security Policy
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");

    // Referrer Policy
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

    // Permissions Policy
    response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

    filterChain.doFilter(request, response);
  }
}
