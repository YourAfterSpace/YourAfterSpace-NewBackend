package com.example.yas_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yourafterspace.yas_backend.dto.LoginRequest;
import com.yourafterspace.yas_backend.dto.SignupRequest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CognitoService DTOs.
 *
 * <p>Note: Full integration tests with Cognito would require: 1. Testcontainers with LocalStack 2.
 * Or actual AWS Cognito test environment 3. Or extensive mocking of the
 * CognitoIdentityProviderClient
 *
 * <p>For now, we recommend testing manually or with integration tests that use a real Cognito User
 * Pool in a test AWS account.
 */
class CognitoServiceTest {

  @Test
  void testSignupRequestValidation() {
    // Test that SignupRequest DTO validation works
    SignupRequest request = new SignupRequest();
    request.setEmail("test@example.com");
    request.setPassword("TestPassword123!");
    request.setUsername("testuser");

    assertThat(request.getEmail()).isEqualTo("test@example.com");
    assertThat(request.getPassword()).isEqualTo("TestPassword123!");
    assertThat(request.getUsername()).isEqualTo("testuser");
  }

  @Test
  void testLoginRequestValidation() {
    // Test that LoginRequest DTO validation works
    LoginRequest request = new LoginRequest();
    request.setUsername("testuser");
    request.setPassword("TestPassword123!");

    assertThat(request.getUsername()).isEqualTo("testuser");
    assertThat(request.getPassword()).isEqualTo("TestPassword123!");
  }
}
