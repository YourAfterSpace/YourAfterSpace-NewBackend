package com.example.yas_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Test configuration that provides mock AWS services for testing.
 *
 * <p>This configuration overrides the production AWS beans with mocked versions to avoid AWS
 * credential and connection issues during testing.
 */
@TestConfiguration
public class TestAwsConfiguration {

  @Bean
  @Primary
  public DynamoDbClient dynamoDbClient() {
    return Mockito.mock(DynamoDbClient.class);
  }

  @Bean
  @Primary
  public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
    return Mockito.mock(CognitoIdentityProviderClient.class);
  }

  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }
}
