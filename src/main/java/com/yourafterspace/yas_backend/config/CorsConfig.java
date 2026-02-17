package com.yourafterspace.yas_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration so the frontend (e.g. Flutter web) can call the backend. Set
 * cors.allowed-origins to your frontend URL (e.g. http://localhost:8080).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${cors.allowed-origins:http://localhost:8080,http://localhost:3000,http://localhost:5000}")
  private String[] allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "x-amzn-oidc-identity", "Authorization")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
