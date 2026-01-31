package com.yourafterspace.yas_backend;

import com.yourafterspace.yas_backend.config.CognitoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main application class for YAS Backend.
 *
 * <p>This is a Spring Boot application that provides REST APIs with AWS Cognito authentication. It
 * can be deployed as a standalone application or as an AWS Lambda function.
 */
@SpringBootApplication
@EnableConfigurationProperties(CognitoProperties.class)
public class YasBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(YasBackendApplication.class, args);
  }
}
