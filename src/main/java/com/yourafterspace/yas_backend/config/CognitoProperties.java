package com.yourafterspace.yas_backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for AWS Cognito.
 *
 * <p>These properties should be set via environment variables in AWS Lambda: -
 * AWS_COGNITO_USER_POOL_ID - AWS_COGNITO_CLIENT_ID - AWS_COGNITO_CLIENT_SECRET (optional) -
 * AWS_REGION
 *
 * <p>For local development, use application-dev.properties.
 */
@ConfigurationProperties(prefix = "aws.cognito")
@Validated
public class CognitoProperties {

  @NotBlank(message = "AWS Cognito User Pool ID is required")
  private String userPoolId;

  @NotBlank(message = "AWS Cognito Client ID is required")
  private String clientId;

  private String clientSecret;

  @NotBlank(message = "AWS Region is required")
  private String region = "us-east-1";

  public String getUserPoolId() {
    return userPoolId;
  }

  public void setUserPoolId(String userPoolId) {
    this.userPoolId = userPoolId;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }
}
