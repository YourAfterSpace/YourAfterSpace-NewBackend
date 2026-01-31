package com.example.yas_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yourafterspace.yas_backend.controller.AuthController;
import com.yourafterspace.yas_backend.service.AuthenticationService;
import com.yourafterspace.yas_backend.util.UserContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private UserContext userContext;

  @Mock private AuthenticationService authenticationService;

  @InjectMocks private AuthController authController;

  @Test
  void getCurrentUser_WhenAuthenticated_ReturnsUserInfo() {
    // Given
    String userId = "test-user-123";
    when(userContext.requireCurrentUserId()).thenReturn(userId);
    when(userContext.getCognitoData(any())).thenReturn(Optional.of("base64-data"));

    // Mock HttpServletRequest
    jakarta.servlet.http.HttpServletRequest request =
        org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);

    // When
    ResponseEntity<?> response = authController.getCurrentUser(request);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
  }

  @Test
  void getCurrentUser_WhenNotAuthenticated_ThrowsException() {
    // Given
    when(userContext.requireCurrentUserId())
        .thenThrow(new IllegalStateException("No authenticated user found"));

    jakarta.servlet.http.HttpServletRequest request =
        org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);

    // When/Then
    assertThatThrownBy(() -> authController.getCurrentUser(request))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getAuthStatus_WhenAuthenticated_ReturnsTrue() {
    // Given
    when(userContext.isAuthenticated()).thenReturn(true);

    // When
    ResponseEntity<?> response = authController.getAuthStatus();

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
  }

  @Test
  void getAuthStatus_WhenNotAuthenticated_ReturnsFalse() {
    // Given
    when(userContext.isAuthenticated()).thenReturn(false);

    // When
    ResponseEntity<?> response = authController.getAuthStatus();

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
  }
}
