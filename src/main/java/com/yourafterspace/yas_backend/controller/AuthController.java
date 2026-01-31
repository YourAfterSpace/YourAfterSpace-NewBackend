package com.yourafterspace.yas_backend.controller;

import com.yourafterspace.yas_backend.dto.ApiResponse;
import com.yourafterspace.yas_backend.dto.AuthResponse;
import com.yourafterspace.yas_backend.dto.ConfirmSignupRequest;
import com.yourafterspace.yas_backend.dto.LoginRequest;
import com.yourafterspace.yas_backend.dto.ResendConfirmationRequest;
import com.yourafterspace.yas_backend.dto.SignupRequest;
import com.yourafterspace.yas_backend.dto.SignupResponse;
import com.yourafterspace.yas_backend.service.AuthenticationService;
import com.yourafterspace.yas_backend.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for authenticated endpoints.
 *
 * <p>All endpoints in this controller require authentication via AWS API Gateway + Cognito. The
 * user identity is automatically extracted from the x-amzn-oidc-identity header by the
 * ApiGatewayAuthFilter, which is set by API Gateway after token validation.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

  private final UserContext userContext;
  private final AuthenticationService authenticationService;

  public AuthController(UserContext userContext, AuthenticationService authenticationService) {
    this.userContext = userContext;
    this.authenticationService = authenticationService;
  }

  /**
   * Sign up a new user.
   *
   * <p>This is a public endpoint that does not require authentication.
   *
   * @param request Signup request containing user details
   * @return Signup response with user information
   */
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signUp(
      @Valid @RequestBody SignupRequest request) {
    logger.info("Signup request received for username: {}", request.getUsername());

    SignupResponse response = authenticationService.signUp(request);

    return ResponseEntity.ok(ApiResponse.success("User registered successfully", response));
  }

  /**
   * Login user and get authentication tokens.
   *
   * <p>This is a public endpoint that does not require authentication.
   *
   * @param request Login request containing username and password
   * @return Authentication response with tokens
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    logger.info("Login request received for username: {}", request.getUsername());

    AuthResponse response =
        authenticationService.login(request.getUsername(), request.getPassword());

    return ResponseEntity.ok(ApiResponse.success("Login successful", response));
  }

  /**
   * Confirm user signup with verification code.
   *
   * <p>This is a public endpoint that does not require authentication.
   *
   * @param request Confirm signup request containing username and confirmation code
   * @return Success message
   */
  @PostMapping("/confirm-signup")
  public ResponseEntity<ApiResponse<Map<String, String>>> confirmSignUp(
      @Valid @RequestBody ConfirmSignupRequest request) {
    logger.info("Confirm signup request received for username: {}", request.getUsername());

    authenticationService.confirmSignUp(request.getUsername(), request.getConfirmationCode());

    Map<String, String> result = new HashMap<>();
    result.put("message", "User account confirmed successfully");
    result.put("username", request.getUsername());

    return ResponseEntity.ok(ApiResponse.success("Account confirmed successfully", result));
  }

  /**
   * Resend confirmation code.
   *
   * <p>This is a public endpoint that does not require authentication.
   *
   * @param request Request containing username to resend confirmation code for
   * @return Success message
   */
  @PostMapping("/resend-confirmation")
  public ResponseEntity<ApiResponse<Map<String, String>>> resendConfirmationCode(
      @Valid @RequestBody ResendConfirmationRequest request) {
    logger.info(
        "Resend confirmation code request received for username: {}", request.getUsername());

    authenticationService.resendConfirmationCode(request.getUsername());

    Map<String, String> result = new HashMap<>();
    result.put("message", "Confirmation code resent successfully");
    result.put("username", request.getUsername());

    return ResponseEntity.ok(ApiResponse.success("Confirmation code resent successfully", result));
  }

  /**
   * Get current authenticated user information.
   *
   * <p>This endpoint demonstrates how to access the authenticated user ID that was validated by API
   * Gateway's Cognito authorizer.
   *
   * @param request HTTP request
   * @return User information including user ID and claims availability
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
      HttpServletRequest request) {
    String userId = userContext.requireCurrentUserId();

    logger.info("Fetching user info for user: {}", userId);

    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put("userId", userId);

    // Check if Cognito claims data is available
    userContext
        .getCognitoData(request)
        .ifPresent(
            data -> {
              userInfo.put("hasClaims", true);
              // Note: claims is base64-encoded JSON, decode on client if needed
              userInfo.put("claimsAvailable", true);
            });

    return ResponseEntity.ok(
        ApiResponse.success("User information retrieved successfully", userInfo));
  }

  /**
   * Check authentication status.
   *
   * @return Simple authentication status
   */
  @GetMapping("/status")
  public ResponseEntity<ApiResponse<Map<String, Boolean>>> getAuthStatus() {
    Map<String, Boolean> status = new HashMap<>();
    status.put("authenticated", userContext.isAuthenticated());

    return ResponseEntity.ok(ApiResponse.success("Authentication status", status));
  }
}
