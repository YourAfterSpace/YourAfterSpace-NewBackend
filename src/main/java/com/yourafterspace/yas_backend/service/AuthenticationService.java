package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.AuthResponse;
import com.yourafterspace.yas_backend.dto.SignupRequest;
import com.yourafterspace.yas_backend.dto.SignupResponse;

/**
 * Interface for authentication operations.
 *
 * <p>This interface defines the contract for authentication services, allowing for different
 * implementations (e.g., Cognito, custom auth).
 */
public interface AuthenticationService {

  /**
   * Sign up a new user.
   *
   * @param request Signup request containing user details
   * @return Signup response with user information
   * @throws com.yourafterspace.yas_backend.exception.BadRequestException if signup fails
   */
  SignupResponse signUp(SignupRequest request);

  /**
   * Authenticate user and return tokens.
   *
   * @param username Username or email
   * @param password User password
   * @return Authentication response with tokens
   * @throws com.yourafterspace.yas_backend.exception.BadRequestException if authentication fails
   */
  AuthResponse login(String username, String password);

  /**
   * Confirm user signup with verification code.
   *
   * @param username Username or email
   * @param confirmationCode Verification code
   * @throws com.yourafterspace.yas_backend.exception.BadRequestException if confirmation fails
   */
  void confirmSignUp(String username, String confirmationCode);

  /**
   * Resend confirmation code.
   *
   * @param username Username or email
   * @throws com.yourafterspace.yas_backend.exception.BadRequestException if resend fails
   */
  void resendConfirmationCode(String username);
}
