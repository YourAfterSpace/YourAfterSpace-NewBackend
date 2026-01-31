package com.example.yas_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourafterspace.yas_backend.controller.AuthController;
import com.yourafterspace.yas_backend.dto.*;
import com.yourafterspace.yas_backend.exception.GlobalExceptionHandler;
import com.yourafterspace.yas_backend.service.AuthenticationService;
import com.yourafterspace.yas_backend.util.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerCognitoTest {

  private MockMvc mockMvc;

  @Mock private AuthenticationService authenticationService;

  @Mock private UserContext userContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    AuthController authController = new AuthController(userContext, authenticationService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void signUp_ValidRequest_ReturnsSuccess() throws Exception {
    // Given
    SignupRequest request = new SignupRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("TestPassword123!");
    request.setFirstName("Test");
    request.setLastName("User");

    SignupResponse response =
        new SignupResponse(
            "user-id-123",
            "testuser",
            "test@example.com",
            false,
            "User successfully registered. Please check your email for verification code.");

    when(authenticationService.signUp(any(SignupRequest.class))).thenReturn(response);

    // When/Then
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value("user-id-123"))
        .andExpect(jsonPath("$.data.username").value("testuser"))
        .andExpect(jsonPath("$.data.email").value("test@example.com"))
        .andExpect(jsonPath("$.data.confirmed").value(false));
  }

  @Test
  void signUp_InvalidRequest_ReturnsBadRequest() throws Exception {
    // Given - missing required fields
    SignupRequest request = new SignupRequest();
    request.setUsername(""); // Invalid: empty username

    // When/Then
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void login_ValidCredentials_ReturnsTokens() throws Exception {
    // Given
    LoginRequest request = new LoginRequest();
    request.setUsername("testuser");
    request.setPassword("TestPassword123!");

    AuthResponse authResponse =
        new AuthResponse(
            "access-token", "id-token", "refresh-token", 3600, "user-id-123", "testuser");

    when(authenticationService.login(anyString(), anyString())).thenReturn(authResponse);

    // When/Then
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.idToken").value("id-token"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(3600))
        .andExpect(jsonPath("$.data.userId").value("user-id-123"))
        .andExpect(jsonPath("$.data.username").value("testuser"));
  }

  @Test
  void login_InvalidCredentials_ReturnsBadRequest() throws Exception {
    // Given
    LoginRequest request = new LoginRequest();
    request.setUsername("testuser");
    request.setPassword("wrongpassword");

    when(authenticationService.login(anyString(), anyString()))
        .thenThrow(
            new com.yourafterspace.yas_backend.exception.BadRequestException(
                "Invalid username or password"));

    // When/Then
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirmSignUp_ValidCode_ReturnsSuccess() throws Exception {
    // Given
    ConfirmSignupRequest request = new ConfirmSignupRequest();
    request.setUsername("testuser");
    request.setConfirmationCode("123456");

    // When/Then
    mockMvc
        .perform(
            post("/api/auth/confirm-signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value("User account confirmed successfully"))
        .andExpect(jsonPath("$.data.username").value("testuser"));
  }

  @Test
  void resendConfirmation_ValidUsername_ReturnsSuccess() throws Exception {
    // Given
    ResendConfirmationRequest request = new ResendConfirmationRequest();
    request.setUsername("testuser");

    // When/Then
    mockMvc
        .perform(
            post("/api/auth/resend-confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value("Confirmation code resent successfully"))
        .andExpect(jsonPath("$.data.username").value("testuser"));
  }
}
