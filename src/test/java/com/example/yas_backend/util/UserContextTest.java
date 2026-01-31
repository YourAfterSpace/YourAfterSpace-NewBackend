package com.example.yas_backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yourafterspace.yas_backend.config.ApiGatewayAuthFilter;
import com.yourafterspace.yas_backend.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class UserContextTest {

  private UserContext userContext;

  @Mock private HttpServletRequest mockRequest;

  @BeforeEach
  void setUp() {
    userContext = new UserContext();
  }

  @Test
  void getUserId_WhenUserIsAuthenticated_ReturnsUserId() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID, "user-123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    Optional<String> userId = userContext.getCurrentUserId();

    // Then
    assertThat(userId).isPresent();
    assertThat(userId.get()).isEqualTo("user-123");
  }

  @Test
  void getUserId_WhenUserIsNotAuthenticated_ReturnsEmpty() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    Optional<String> userId = userContext.getCurrentUserId();

    // Then
    assertThat(userId).isEmpty();
  }

  @Test
  void requireCurrentUserId_WhenAuthenticated_ReturnsUserId() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID, "user-456");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    String userId = userContext.requireCurrentUserId();

    // Then
    assertThat(userId).isEqualTo("user-456");
  }

  @Test
  void requireCurrentUserId_WhenNotAuthenticated_ThrowsException() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When/Then
    assertThatThrownBy(() -> userContext.requireCurrentUserId())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No authenticated user found");
  }

  @Test
  void getUserId_WithExplicitRequest_WhenAuthenticated_ReturnsUserId() {
    // Given
    when(mockRequest.getAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID))
        .thenReturn("user-789");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

    // When
    Optional<String> userId = userContext.getCurrentUserId();

    // Then
    assertThat(userId).isPresent();
    assertThat(userId.get()).isEqualTo("user-789");
  }

  @Test
  void getCognitoData_WhenAvailable_ReturnsData() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_COGNITO_DATA, "base64-encoded-data");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    Optional<String> data = userContext.getCognitoData(request);

    // Then
    assertThat(data).isPresent();
    assertThat(data.get()).isEqualTo("base64-encoded-data");
  }

  @Test
  void isAuthenticated_WhenUserIsAuthenticated_ReturnsTrue() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID, "user-123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    boolean authenticated = userContext.isAuthenticated();

    // Then
    assertThat(authenticated).isTrue();
  }

  @Test
  void isAuthenticated_WhenUserIsNotAuthenticated_ReturnsFalse() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    boolean authenticated = userContext.isAuthenticated();

    // Then
    assertThat(authenticated).isFalse();
  }

  @Test
  void getUserId_WithNullRequest_ReturnsEmpty() {
    // Given
    RequestContextHolder.resetRequestAttributes();

    // When
    Optional<String> userId = userContext.getCurrentUserId();

    // Then
    assertThat(userId).isEmpty();
  }
}
