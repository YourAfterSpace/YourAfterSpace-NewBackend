package com.yourafterspace.yas_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourafterspace.yas_backend.config.CognitoProperties;
import com.yourafterspace.yas_backend.dto.AuthResponse;
import com.yourafterspace.yas_backend.dto.SignupRequest;
import com.yourafterspace.yas_backend.dto.SignupResponse;
import com.yourafterspace.yas_backend.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

/**
 * Service implementation for AWS Cognito authentication.
 *
 * <p>This service handles user registration, authentication, and account confirmation using AWS
 * Cognito as the identity provider.
 */
@Service
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoService extends BaseService implements AuthenticationService {

  private static final Logger logger = LoggerFactory.getLogger(CognitoService.class);

  private final CognitoIdentityProviderClient cognitoClient;
  private final CognitoProperties cognitoProperties;
  private final ObjectMapper objectMapper;

  public CognitoService(
      CognitoIdentityProviderClient cognitoClient, CognitoProperties cognitoProperties) {
    this.cognitoClient = cognitoClient;
    this.cognitoProperties = cognitoProperties;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Sign up a new user in Cognito
   *
   * <p>Note: If your Cognito User Pool is configured to require email as username, the email
   * address will be used as the username automatically.
   */
  public SignupResponse signUp(SignupRequest request) {
    try {
      // Use email as username if Cognito requires it (common configuration)
      // This handles the case where Cognito User Pool is set to "email" as username attribute
      String cognitoUsername = request.getEmail();

      logger.info("Attempting to sign up user with email: {}", request.getEmail());

      List<AttributeType> userAttributes = new ArrayList<>();
      userAttributes.add(AttributeType.builder().name("email").value(request.getEmail()).build());

      if (request.getFirstName() != null && !request.getFirstName().isEmpty()) {
        userAttributes.add(
            AttributeType.builder().name("given_name").value(request.getFirstName()).build());
      }

      if (request.getLastName() != null && !request.getLastName().isEmpty()) {
        userAttributes.add(
            AttributeType.builder().name("family_name").value(request.getLastName()).build());
      }

      if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty()) {
        userAttributes.add(
            AttributeType.builder().name("phone_number").value(request.getPhoneNumber()).build());
      }

      SignUpRequest.Builder signUpRequestBuilder =
          SignUpRequest.builder()
              .clientId(cognitoProperties.getClientId())
              .username(cognitoUsername) // Use email as username
              .password(request.getPassword())
              .userAttributes(userAttributes);

      // Add client secret if configured (for confidential clients)
      if (cognitoProperties.getClientSecret() != null
          && !cognitoProperties.getClientSecret().isEmpty()) {
        signUpRequestBuilder.secretHash(calculateSecretHash(cognitoUsername));
      }

      SignUpResponse response = cognitoClient.signUp(signUpRequestBuilder.build());

      boolean confirmed = response.userConfirmed();
      String message =
          confirmed
              ? "User successfully registered and confirmed"
              : "User successfully registered. Please check your email for verification code.";

      logger.info("User signed up successfully: {}", request.getEmail());

      return new SignupResponse(
          response.userSub(),
          cognitoUsername, // Return email as username
          request.getEmail(),
          confirmed,
          message);

    } catch (UsernameExistsException e) {
      logger.error("Email already exists: {}", request.getEmail());
      throw new BadRequestException("Email already exists");
    } catch (InvalidPasswordException e) {
      logger.error("Invalid password for user: {}", request.getEmail());
      // Extract more details from the exception message if available
      String errorMessage = e.getMessage();
      String detailedMessage = "Password does not meet requirements";

      if (errorMessage != null) {
        // Cognito typically provides detailed error messages
        if (errorMessage.contains("length")) {
          detailedMessage += ". Password must be at least 8 characters long.";
        } else if (errorMessage.contains("uppercase")) {
          detailedMessage += ". Password must contain at least one uppercase letter.";
        } else if (errorMessage.contains("lowercase")) {
          detailedMessage += ". Password must contain at least one lowercase letter.";
        } else if (errorMessage.contains("number") || errorMessage.contains("digit")) {
          detailedMessage += ". Password must contain at least one number.";
        } else if (errorMessage.contains("symbol") || errorMessage.contains("special")) {
          detailedMessage += ". Password must contain at least one special character.";
        } else {
          // Include the original message for more context
          detailedMessage += " " + errorMessage;
        }
      } else {
        // Default message with common requirements
        detailedMessage +=
            ". Password must be at least 8 characters and contain uppercase, lowercase, number, and special character.";
      }

      throw new BadRequestException(detailedMessage);
    } catch (InvalidParameterException e) {
      logger.error("Invalid parameter during signup: {}", e.getMessage());
      throw new BadRequestException("Invalid signup parameters: " + e.getMessage());
    } catch (Exception e) {
      logger.error("Error during signup for user: {}", request.getEmail(), e);
      throw new BadRequestException("Failed to sign up user: " + e.getMessage());
    }
  }

  /**
   * Authenticate user and return tokens
   *
   * <p>Note: The username parameter can be either the email address or username, depending on your
   * Cognito User Pool configuration. If your pool requires email as username, pass the email
   * address here.
   */
  public AuthResponse login(String username, String password) {
    try {
      logger.info("Attempting to login user: {}", username);

      Map<String, String> authParams = new java.util.HashMap<>();
      authParams.put("USERNAME", username);
      authParams.put("PASSWORD", password);

      // Add client secret if configured
      if (cognitoProperties.getClientSecret() != null
          && !cognitoProperties.getClientSecret().isEmpty()) {
        authParams.put("SECRET_HASH", calculateSecretHash(username));
      }

      InitiateAuthRequest.Builder authRequestBuilder =
          InitiateAuthRequest.builder()
              .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
              .clientId(cognitoProperties.getClientId())
              .authParameters(authParams);

      InitiateAuthResponse response = cognitoClient.initiateAuth(authRequestBuilder.build());

      AuthenticationResultType authResult = response.authenticationResult();

      // Get user details
      String userId = getUserIdFromToken(authResult.idToken());
      String usernameFromToken = getUsernameFromToken(authResult.idToken());

      logger.info("User logged in successfully: {}", username);

      return new AuthResponse(
          authResult.accessToken(),
          authResult.idToken(),
          authResult.refreshToken(),
          authResult.expiresIn(),
          userId,
          usernameFromToken != null ? usernameFromToken : username);

    } catch (NotAuthorizedException e) {
      logger.error("Invalid credentials for user: {}", username);
      throw new BadRequestException("Invalid username or password");
    } catch (UserNotConfirmedException e) {
      logger.error("User not confirmed: {}", username);
      throw new BadRequestException("User account is not confirmed. Please verify your email.");
    } catch (UserNotFoundException e) {
      logger.error("User not found: {}", username);
      throw new BadRequestException("Invalid username or password");
    } catch (Exception e) {
      logger.error("Error during login for user: {}", username, e);
      throw new BadRequestException("Failed to login: " + e.getMessage());
    }
  }

  /** Confirm user signup with verification code */
  public void confirmSignUp(String username, String confirmationCode) {
    try {
      logger.info("Attempting to confirm signup for user: {}", username);

      ConfirmSignUpRequest.Builder confirmRequestBuilder =
          ConfirmSignUpRequest.builder()
              .clientId(cognitoProperties.getClientId())
              .username(username)
              .confirmationCode(confirmationCode);

      // Add client secret if configured
      if (cognitoProperties.getClientSecret() != null
          && !cognitoProperties.getClientSecret().isEmpty()) {
        confirmRequestBuilder.secretHash(calculateSecretHash(username));
      }

      cognitoClient.confirmSignUp(confirmRequestBuilder.build());

      logger.info("User signup confirmed successfully: {}", username);

    } catch (CodeMismatchException e) {
      logger.error("Invalid confirmation code for user: {}", username);
      throw new BadRequestException("Invalid confirmation code");
    } catch (ExpiredCodeException e) {
      logger.error("Expired confirmation code for user: {}", username);
      throw new BadRequestException("Confirmation code has expired");
    } catch (UserNotFoundException e) {
      logger.error("User not found: {}", username);
      throw new BadRequestException("User not found");
    } catch (Exception e) {
      logger.error("Error confirming signup for user: {}", username, e);
      throw new BadRequestException("Failed to confirm signup: " + e.getMessage());
    }
  }

  /** Resend confirmation code */
  public void resendConfirmationCode(String username) {
    try {
      logger.info("Attempting to resend confirmation code for user: {}", username);

      ResendConfirmationCodeRequest.Builder resendRequestBuilder =
          ResendConfirmationCodeRequest.builder()
              .clientId(cognitoProperties.getClientId())
              .username(username);

      // Add client secret if configured
      if (cognitoProperties.getClientSecret() != null
          && !cognitoProperties.getClientSecret().isEmpty()) {
        resendRequestBuilder.secretHash(calculateSecretHash(username));
      }

      cognitoClient.resendConfirmationCode(resendRequestBuilder.build());

      logger.info("Confirmation code resent successfully for user: {}", username);

    } catch (UserNotFoundException e) {
      logger.error("User not found: {}", username);
      throw new BadRequestException("User not found");
    } catch (LimitExceededException e) {
      logger.error("Rate limit exceeded for user: {}", username);
      throw new BadRequestException("Too many requests. Please try again later.");
    } catch (Exception e) {
      logger.error("Error resending confirmation code for user: {}", username, e);
      throw new BadRequestException("Failed to resend confirmation code: " + e.getMessage());
    }
  }

  /**
   * Calculate secret hash for Cognito client secret authentication.
   *
   * @param username Username to calculate hash for
   * @return Base64-encoded secret hash
   */
  private String calculateSecretHash(String username) {
    String secret = cognitoProperties.getClientSecret();
    if (secret == null || secret.isEmpty()) {
      return null;
    }
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      javax.crypto.spec.SecretKeySpec secretKey =
          new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
      mac.init(secretKey);
      mac.update(username.getBytes("UTF-8"));
      mac.update(cognitoProperties.getClientId().getBytes("UTF-8"));
      byte[] rawHmac = mac.doFinal();
      return java.util.Base64.getEncoder().encodeToString(rawHmac);
    } catch (Exception e) {
      logger.error("Error calculating secret hash", e);
      throw new RuntimeException("Failed to calculate secret hash", e);
    }
  }

  /**
   * Extract user ID from ID token Note: This decodes the JWT without validation. In production,
   * consider using a JWT library to validate the token signature and expiration.
   */
  private String getUserIdFromToken(String idToken) {
    try {
      String[] parts = idToken.split("\\.");
      if (parts.length > 1) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        JsonNode jsonNode = objectMapper.readTree(payload);
        if (jsonNode.has("sub")) {
          return jsonNode.get("sub").asText();
        }
      }
    } catch (Exception e) {
      logger.warn("Could not extract user ID from token", e);
    }
    return null;
  }

  /** Extract username from ID token */
  private String getUsernameFromToken(String idToken) {
    try {
      String[] parts = idToken.split("\\.");
      if (parts.length > 1) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        JsonNode jsonNode = objectMapper.readTree(payload);
        // Try cognito:username first, then fall back to other username fields
        if (jsonNode.has("cognito:username")) {
          return jsonNode.get("cognito:username").asText();
        } else if (jsonNode.has("username")) {
          return jsonNode.get("username").asText();
        } else if (jsonNode.has("email")) {
          return jsonNode.get("email").asText();
        }
      }
    } catch (Exception e) {
      logger.warn("Could not extract username from token", e);
    }
    return null;
  }
}
