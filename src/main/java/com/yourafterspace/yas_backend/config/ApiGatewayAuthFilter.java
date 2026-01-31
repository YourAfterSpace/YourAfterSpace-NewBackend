package com.yourafterspace.yas_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts user identity from API Gateway headers.
 *
 * <p>When AWS API Gateway validates a Cognito token, it adds: - x-amzn-oidc-identity: The Cognito
 * user ID (sub claim) - x-amzn-oidc-data: Base64-encoded JWT claims
 *
 * <p>This filter extracts these headers and makes them available via UserContext.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // Run after RequestIdFilter
public class ApiGatewayAuthFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(ApiGatewayAuthFilter.class);

  public static final String HEADER_IDENTITY = "x-amzn-oidc-identity";
  public static final String HEADER_DATA = "x-amzn-oidc-data";
  public static final String REQUEST_ATTR_USER_ID = "com.example.yas_backend.userId";
  public static final String REQUEST_ATTR_COGNITO_DATA = "com.example.yas_backend.cognitoData";
  public static final String MDC_USER_ID_KEY = "userId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String userId = request.getHeader(HEADER_IDENTITY);
    String cognitoData = request.getHeader(HEADER_DATA);

    if (userId != null && !userId.isBlank()) {
      // Store in request attributes for UserContext access
      request.setAttribute(REQUEST_ATTR_USER_ID, userId);
      request.setAttribute(REQUEST_ATTR_COGNITO_DATA, cognitoData);

      // Add to MDC for logging
      MDC.put(MDC_USER_ID_KEY, userId);

      logger.debug("Authenticated request from user: {}", userId);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      // Clean up MDC
      MDC.remove(MDC_USER_ID_KEY);
    }
  }
}
