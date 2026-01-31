package com.yourafterspace.yas_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds a request correlation id to every request/response and the logging MDC. - If the client
 * sends X-Request-Id, we use it. - Otherwise we generate one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER_REQUEST_ID = "X-Request-Id";
  public static final String MDC_REQUEST_ID_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(HEADER_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }

    MDC.put(MDC_REQUEST_ID_KEY, requestId);
    response.setHeader(HEADER_REQUEST_ID, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_REQUEST_ID_KEY);
    }
  }
}
