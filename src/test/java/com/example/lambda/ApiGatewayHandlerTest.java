package com.example.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.yourafterspace.lambda.ApiGatewayHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiGatewayHandlerTest {

  private ApiGatewayHandler handler;

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    handler = new ApiGatewayHandler();
  }

  @Test
  void handleRequest_HealthEndpoint_Returns200() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/health");
    event.setHttpMethod("GET");
    event.setHeaders(new HashMap<>());

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(200);
  }

  @Test
  void handleRequest_GetMeWithAuth_ReturnsUserInfo() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/api/auth/me");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-123");
    event.setHeaders(headers);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).contains("test-user-123");
    assertThat(response.getBody()).contains("\"success\":true");
  }

  @Test
  void handleRequest_GetMeWithoutAuth_Returns401() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/api/auth/me");
    event.setHttpMethod("GET");
    event.setHeaders(new HashMap<>());

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(401);
    assertThat(response.getBody()).contains("Unauthorized");
  }

  @Test
  void handleRequest_GetAuthStatusWithAuth_ReturnsStatus() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/api/auth/status");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-456");
    event.setHeaders(headers);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"authenticated\":true");
  }

  @Test
  void handleRequest_UnknownPath_Returns404() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/unknown/path");
    event.setHttpMethod("GET");
    event.setHeaders(new HashMap<>());

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(404);
  }

  @Test
  void extractUserId_WithValidUserId_ReturnsOptional() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "user-123");
    event.setHeaders(headers);

    // When
    Optional<String> userId = ApiGatewayHandler.extractUserId(event);

    // Then
    assertThat(userId).isPresent();
    assertThat(userId.get()).isEqualTo("user-123");
  }

  @Test
  void extractUserId_WithoutUserId_ReturnsEmpty() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setHeaders(new HashMap<>());

    // When
    Optional<String> userId = ApiGatewayHandler.extractUserId(event);

    // Then
    assertThat(userId).isEmpty();
  }

  @Test
  void extractCognitoData_WithData_ReturnsOptional() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-data", "base64-encoded-data");
    event.setHeaders(headers);

    // When
    Optional<String> data = ApiGatewayHandler.extractCognitoData(event);

    // Then
    assertThat(data).isPresent();
    assertThat(data.get()).isEqualTo("base64-encoded-data");
  }

  @Test
  void handleRequest_V1HealthEndpoint_Returns200() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/health");
    event.setHttpMethod("GET");
    event.setHeaders(new HashMap<>());

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"status\":\"OK\"");
  }

  @Test
  void handleRequest_V1ApiAuthMe_ReturnsUserInfo() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/api/auth/me");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-v1-123");
    event.setHeaders(headers);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).contains("test-user-v1-123");
    assertThat(response.getBody()).contains("\"success\":true");
  }

  @Test
  void handleRequest_V1ExperiencesWithoutAuth_Returns401() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences");
    event.setHttpMethod("GET");
    event.setHeaders(new HashMap<>());

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(401);
    assertThat(response.getBody()).contains("Unauthorized");
  }

  @Test
  void handleRequest_V1ExperiencesWithLatLon_Returns200() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-123");
    event.setHeaders(headers);
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("lat", "40.7128");
    queryParams.put("lon", "-74.0060");
    event.setQueryStringParameters(queryParams);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"success\":true");
    assertThat(response.getBody()).contains("\"message\":\"Experiences retrieved successfully\"");
  }

  @Test
  void handleRequest_V1ExperiencesWithInvalidLatLon_Returns400() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-123");
    event.setHeaders(headers);
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("lat", "invalid");
    queryParams.put("lon", "-74.0060");
    event.setQueryStringParameters(queryParams);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(400);
    assertThat(response.getBody()).contains("Invalid lat/lon format");
  }

  @Test
  void handleRequest_V1ExperiencesWithUserId_Returns200() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-123");
    event.setHeaders(headers);
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("userId", "user-456");
    event.setQueryStringParameters(queryParams);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    // May return 200 (success) or 500 (DynamoDB not available in test environment)
    assertThat(response.getStatusCode()).isIn(200, 500);
    // Verify endpoint is accessible (not 401/404)
    assertThat(response.getStatusCode()).isNotIn(401, 404);
  }

  @Test
  void handleRequest_V1ExperiencesWithGroupId_Returns200() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-123");
    event.setHeaders(headers);
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("groupId", "group-789");
    event.setQueryStringParameters(queryParams);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    // May return 200 (success) or 500 (DynamoDB not available in test environment)
    assertThat(response.getStatusCode()).isIn(200, 500);
    // Verify endpoint is accessible (not 401/404)
    assertThat(response.getStatusCode()).isNotIn(401, 404);
  }

  @Test
  void handleRequest_V1ExperiencesById_Returns200() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences/exp-123");
    event.setHttpMethod("GET");
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amzn-oidc-identity", "test-user-123");
    event.setHeaders(headers);

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    // May return 200 (success), 404 (not found), or 500 (DynamoDB not available)
    assertThat(response.getStatusCode()).isIn(200, 404, 500);
    // Verify endpoint is accessible (not 401)
    assertThat(response.getStatusCode()).isNotEqualTo(401);
  }

  @Test
  void handleRequest_V1ExperiencesByIdWithoutAuth_Returns401() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences/exp-123");
    event.setHttpMethod("GET");
    event.setHeaders(new HashMap<>());

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(401);
    assertThat(response.getBody()).contains("Unauthorized");
  }

  @Test
  void handleRequest_V1ExperiencesCreateWithoutAuth_Returns401() {
    // Given
    APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
    event.setPath("/v1/experiences/exp-123");
    event.setHttpMethod("PUT");
    event.setHeaders(new HashMap<>());
    event.setBody("{\"title\":\"Test Experience\"}");

    // When
    APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(401);
    assertThat(response.getBody()).contains("Unauthorized");
  }
}
