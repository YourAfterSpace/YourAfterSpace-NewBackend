package com.yourafterspace.yas_backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class ApiGatewayAuthFilterTest {

  private ApiGatewayAuthFilter filter;

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    filter = new ApiGatewayAuthFilter();
    MDC.clear();
  }

  @Test
  void doFilterInternal_WhenCognitoHeaderPresent_SetsUserAttribute() throws Exception {
    // Given
    String userId = "cognito-user-123";
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_IDENTITY)).thenReturn(userId);
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_DATA)).thenReturn(null);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(request).setAttribute(eq(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID), eq(userId));
    verify(filterChain).doFilter(request, response);
    assertThat(MDC.get("userId")).isNull(); // MDC cleared in finally block
  }

  @Test
  void doFilterInternal_WhenCognitoDataPresent_SetsBothAttributes() throws Exception {
    // Given
    String userId = "cognito-user-456";
    String cognitoData = "base64-encoded-claims";
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_IDENTITY)).thenReturn(userId);
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_DATA)).thenReturn(cognitoData);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(request).setAttribute(eq(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID), eq(userId));
    verify(request)
        .setAttribute(eq(ApiGatewayAuthFilter.REQUEST_ATTR_COGNITO_DATA), eq(cognitoData));
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_WhenNoHeaders_DoesNotSetAttributes() throws Exception {
    // Given
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_IDENTITY)).thenReturn(null);
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_DATA)).thenReturn(null);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(request, never()).setAttribute(eq(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID), any());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_WhenHeaderIsBlank_DoesNotSetAttributes() throws Exception {
    // Given
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_IDENTITY)).thenReturn("  ");
    when(request.getHeader(ApiGatewayAuthFilter.HEADER_DATA)).thenReturn(null);

    // When
    filter.doFilterInternal(request, response, filterChain);

    // Then
    verify(request, never()).setAttribute(eq(ApiGatewayAuthFilter.REQUEST_ATTR_USER_ID), any());
    verify(filterChain).doFilter(request, response);
  }
}
