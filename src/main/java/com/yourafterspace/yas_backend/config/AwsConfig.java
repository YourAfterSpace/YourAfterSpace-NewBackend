package com.yourafterspace.yas_backend.config;

import com.yourafterspace.yas_backend.dao.ExperienceDao;
import com.yourafterspace.yas_backend.dao.UserExperienceDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * AWS configuration for Cognito and other AWS services.
 *
 * <p>This configuration uses environment variables for sensitive data: - AWS_COGNITO_USER_POOL_ID -
 * AWS_COGNITO_CLIENT_ID - AWS_COGNITO_CLIENT_SECRET (optional, for confidential clients) -
 * AWS_REGION
 *
 * <p>For local development, these can be set in application-dev.properties. For AWS Lambda, these
 * should be set as environment variables.
 */
@Configuration
public class AwsConfig {

  @Bean
  public CognitoIdentityProviderClient cognitoIdentityProviderClient(
      @Value("${aws.region:us-east-1}") String region) {
    return CognitoIdentityProviderClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  public DynamoDbClient dynamoDbClient(@Value("${aws.region:us-east-1}") String region) {
    return DynamoDbClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  public UserExperienceDao userExperienceDao(
      DynamoDbClient dynamoDbClient,
      @Value("${aws.dynamodb.user-profile-table:YourAfterSpace}") String tableName) {
    return new UserExperienceDao(dynamoDbClient, tableName);
  }

  @Bean
  public ExperienceDao experienceDao(
      DynamoDbClient dynamoDbClient,
      @Value("${aws.dynamodb.user-profile-table:YourAfterSpace}") String tableName) {
    return new ExperienceDao(dynamoDbClient, tableName);
  }
}
