package com.yourafterspace.yas_backend.util;

import com.yourafterspace.yas_backend.config.ApiGatewayAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for accessing the current authenticated user's context.
 *
 * <p>The user identity is extracted from API Gateway headers by ApiGatewayAuthFilter and made
 * available through this service.
 */
@Component
public class UserContext {

  private static final Logger logger = LoggerFactory.getLogger(UserContext.class);

  /**
   * Get the current user ID if authenticated.
   *
   * @return Optional containing the user ID, or empty if not authenticated
   */
  public Optional<String> getCurrentUserId() {
    return getRequest()
        .map(request -> (String) request.getAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID))
        .filter(userId -> userId != null && !userId.isBlank());
  }

  /**
   * Get the current user ID, throwing an exception if not authenticated.
   *
   * @return The user ID
   * @throws IllegalStateException if no authenticated user is found
   */
  public String requireCurrentUserId() {
    return getCurrentUserId()
        .orElseThrow(() -> new IllegalStateException("No authenticated user found"));
  }

  /**
   * Check if the current request is authenticated.
   *
   * @return true if authenticated, false otherwise
   */
  public boolean isAuthenticated() {
    return getCurrentUserId().isPresent();
  }

  /**
   * Get the Cognito data (base64-encoded JWT claims) if available.
   *
   * @param request HTTP request
   * @return Optional containing the Cognito data, or empty if not available
   */
  public Optional<String> getCognitoData(HttpServletRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    String data = (String) request.getAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_COGNITO_DATA);
    return data != null && !data.isBlank() ? Optional.of(data) : Optional.empty();
  }

  /**
   * Get the current HttpServletRequest from RequestContextHolder.
   *
   * @return Optional containing the request, or empty if not available
   */
  private Optional<HttpServletRequest> getRequest() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      return Optional.of(((ServletRequestAttributes) requestAttributes).getRequest());
    }
    return Optional.empty();
  }
}
