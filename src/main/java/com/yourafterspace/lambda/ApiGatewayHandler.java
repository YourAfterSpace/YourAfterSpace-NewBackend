package com.yourafterspace.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yourafterspace.yas_backend.dao.ExperienceDao;
import com.yourafterspace.yas_backend.dao.GroupDao;
import com.yourafterspace.yas_backend.dao.GroupExperienceDao;
import com.yourafterspace.yas_backend.dao.UserExperienceDao;
import com.yourafterspace.yas_backend.dao.VenueLocationDao;
import com.yourafterspace.yas_backend.dto.UserProfileRequest;
import com.yourafterspace.yas_backend.dto.UserProfileResponse;
import com.yourafterspace.yas_backend.model.Experience;
import com.yourafterspace.yas_backend.model.Group;
import com.yourafterspace.yas_backend.model.GroupExperience;
import com.yourafterspace.yas_backend.model.UserExperience;
import com.yourafterspace.yas_backend.model.UserExperience.UserExperienceStatus;
import com.yourafterspace.yas_backend.model.UserProfile;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import com.yourafterspace.yas_backend.model.VenueLocation;
import com.yourafterspace.yas_backend.util.GeohashUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Lambda handler for API Gateway requests with Cognito authentication.
 *
 * <p>This handler processes requests from API Gateway after Cognito token validation. API Gateway
 * passes the user identity in the x-amzn-oidc-identity header.
 *
 * <p>Flow: 1. UI gets token from AWS Cognito 2. UI passes token to API Gateway 3. API Gateway
 * validates token with Cognito authorizer 4. API Gateway invokes this Lambda with user identity in
 * headers 5. Lambda extracts user ID and processes the request
 */
public class ApiGatewayHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper objectMapper;
  private static final ObjectWriter objectWriter;
  private static final DynamoDbClient dynamoDbClient;
  private static final String TABLE_NAME;

  // DAO instances (reused across invocations for better performance)
  private static final GroupDao groupDao;
  private static final VenueLocationDao venueLocationDao;
  private static final GroupExperienceDao groupExperienceDao;
  private static final UserExperienceDao userExperienceDao;
  private static final ExperienceDao experienceDao;

  /** Header name set by API Gateway when using Cognito authorizer */
  private static final String HEADER_COGNITO_IDENTITY = "x-amzn-oidc-identity";

  /** Header name containing base64-encoded JWT claims */
  private static final String HEADER_COGNITO_DATA = "x-amzn-oidc-data";

  /** Default DynamoDB table name */
  private static final String DEFAULT_TABLE_NAME = "YAS-DB";

  static {
    // Initialize ObjectMapper with JavaTimeModule for LocalDate support
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectWriter = objectMapper.writer();

    // Initialize DynamoDB client (reused across invocations)
    String region = System.getenv("AWS_REGION");
    if (region == null || region.isEmpty()) {
      region = "eu-west-2"; // Default region
    }
    dynamoDbClient =
        DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    // Get table name from environment variable or use default
    String envTableName = System.getenv("AWS_DYNAMODB_USER_PROFILE_TABLE");
    if (envTableName == null || envTableName.isEmpty()) {
      // Try alternative property name
      envTableName = System.getenv("DYNAMODB_USER_PROFILE_TABLE");
    }
    TABLE_NAME =
        (envTableName != null && !envTableName.isEmpty()) ? envTableName : DEFAULT_TABLE_NAME;

    // Initialize DAOs (reused across invocations)
    groupDao = new GroupDao(dynamoDbClient, TABLE_NAME);
    venueLocationDao = new VenueLocationDao(dynamoDbClient, TABLE_NAME);
    groupExperienceDao = new GroupExperienceDao(dynamoDbClient, TABLE_NAME);
    userExperienceDao = new UserExperienceDao(dynamoDbClient, TABLE_NAME);
    experienceDao = new ExperienceDao(dynamoDbClient, TABLE_NAME);

    // Log table name for debugging (will appear in CloudWatch logs)
    System.out.println("DynamoDB Table Name: " + TABLE_NAME);
    System.out.println("DynamoDB Region: " + region);
    System.out.println("DAOs initialized successfully");
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    try {
      String path = input.getPath();
      String httpMethod = input.getHttpMethod();
      Map<String, String> headers =
          input.getHeaders() != null ? input.getHeaders() : new HashMap<>();
      String body = input.getBody();

      // Extract user identity from API Gateway header (set by Cognito authorizer)
      // Use case-insensitive lookup to handle API Gateway test console scenarios
      String userId = getHeaderCaseInsensitive(headers, HEADER_COGNITO_IDENTITY);
      String cognitoData = getHeaderCaseInsensitive(headers, HEADER_COGNITO_DATA);

      // Fallback for API Gateway test console: check for UserId header or query parameter
      if ((userId == null || userId.isBlank()) && headers != null) {
        userId = getHeaderCaseInsensitive(headers, "UserId");
      }
      if ((userId == null || userId.isBlank()) && input.getQueryStringParameters() != null) {
        userId = input.getQueryStringParameters().get("userId");
      }

      // Log request for debugging
      if (context != null && context.getLogger() != null) {
        context
            .getLogger()
            .log(
                String.format(
                    "Request: %s %s, User: %s",
                    httpMethod, path, userId != null ? userId : "anonymous"));
        context.getLogger().log("DEBUG: Raw path from API Gateway: [" + path + "]");
        // Debug: Log header extraction
        context.getLogger().log("DEBUG: Looking for header: " + HEADER_COGNITO_IDENTITY);
        context
            .getLogger()
            .log("DEBUG: Headers map keys: " + (headers != null ? headers.keySet() : "null"));
        context.getLogger().log("DEBUG: Extracted userId: " + (userId != null ? userId : "null"));
        // Check if header exists with different case
        if (headers != null) {
          for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase().contains("oidc")
                || entry.getKey().toLowerCase().contains("identity")) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: Found OIDC-related header: "
                          + entry.getKey()
                          + " = "
                          + entry.getValue());
            }
          }
        }
        context.getLogger().log("DEBUG: Raw userId from header: [" + userId + "]");
        if (userId != null) {
          context.getLogger().log("DEBUG: userId length: " + userId.length());
          context
              .getLogger()
              .log("DEBUG: userId bytes: " + java.util.Arrays.toString(userId.getBytes()));
        }
        // Log all headers for debugging (useful for API Gateway test console)
        if (headers != null && !headers.isEmpty()) {
          context.getLogger().log("DEBUG: All headers: " + headers.keySet());
        }
      }

      // Route requests based on path
      Map<String, String> queryParams =
          input.getQueryStringParameters() != null
              ? input.getQueryStringParameters()
              : new HashMap<>();

      // Debug: Log parsed query parameters
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("DEBUG: Parsed queryParams map: " + queryParams);
        context.getLogger().log("DEBUG: queryParams keys: " + queryParams.keySet());
        if (queryParams.containsKey("lat")) {
          String latValue = queryParams.get("lat");
          context
              .getLogger()
              .log(
                  "DEBUG: queryParams.get('lat') = ["
                      + latValue
                      + "], length="
                      + (latValue != null ? latValue.length() : "null"));
        }
        if (queryParams.containsKey("lon")) {
          String lonValue = queryParams.get("lon");
          context
              .getLogger()
              .log(
                  "DEBUG: queryParams.get('lon') = ["
                      + lonValue
                      + "], length="
                      + (lonValue != null ? lonValue.length() : "null"));
        }
        if (queryParams.containsKey("radius")) {
          String radiusValue = queryParams.get("radius");
          context
              .getLogger()
              .log(
                  "DEBUG: queryParams.get('radius') = ["
                      + radiusValue
                      + "], length="
                      + (radiusValue != null ? radiusValue.length() : "null"));
        }
      }

      // Also get path parameters if available (from API Gateway resource configuration)
      Map<String, String> pathParameters =
          input.getPathParameters() != null ? input.getPathParameters() : new HashMap<>();
      if (context != null && context.getLogger() != null && !pathParameters.isEmpty()) {
        context.getLogger().log("DEBUG: Path parameters from API Gateway: " + pathParameters);
      }

      // Get multi-value query string parameters for fallback parsing
      Map<String, List<String>> multiValueQueryParams =
          input.getMultiValueQueryStringParameters() != null
              ? input.getMultiValueQueryStringParameters()
              : new HashMap<>();

      return routeRequest(
          path,
          httpMethod,
          body,
          userId,
          cognitoData,
          headers,
          queryParams,
          pathParameters,
          multiValueQueryParams,
          context);

    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Routes requests to appropriate handlers based on path and method. Supports both /v1 and
   * non-versioned paths.
   */
  private APIGatewayProxyResponseEvent routeRequest(
      String path,
      String httpMethod,
      String body,
      String userId,
      String cognitoData,
      Map<String, String> headers,
      Map<String, String> queryParams,
      Map<String, String> pathParameters,
      Map<String, List<String>> multiValueQueryParams,
      Context context) {

    if (path == null) {
      return createErrorResponse(404, "Not Found", "Path is required");
    }

    // Normalize path (handle both /v1 and non-versioned paths)
    String normalizedPath = normalizePath(path);

    // Health check endpoint (public, no auth required)
    // Supports: /health, /v1/health
    if (normalizedPath.equals("/health") && "GET".equals(httpMethod)) {
      return createSuccessResponse(200, Map.of("status", "OK"), "application/json");
    }

    // API endpoints (require authentication)
    // Supports: /api/*, /v1/api/*
    if (normalizedPath.startsWith("/api")) {
      if (userId == null || userId.isBlank()) {
        return createErrorResponse(401, "Unauthorized", "Authentication required");
      }

      // /api/auth/me endpoint
      if ("/api/auth/me".equals(normalizedPath) && "GET".equals(httpMethod)) {
        return handleGetMe(userId, cognitoData);
      }

      // /api/auth/status endpoint
      if ("/api/auth/status".equals(normalizedPath) && "GET".equals(httpMethod)) {
        return handleGetAuthStatus(userId);
      }

      // Add more endpoints here as needed
    }

    // User endpoints (require authentication)
    // Supports: /users/*, /v1/users/*
    if (normalizedPath.startsWith("/users")) {
      // /users/profile endpoint - accepts UserId header or Cognito identity
      if ("/users/profile".equals(normalizedPath)) {
        // Check for UserId header first, then fall back to Cognito identity
        String headerUserId = headers.get("UserId");
        String effectiveUserId =
            (headerUserId != null && !headerUserId.isBlank()) ? headerUserId : userId;
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
          return createErrorResponse(401, "Unauthorized", "Authentication required");
        }

        if ("GET".equals(httpMethod)) {
          return handleGetUserProfile(effectiveUserId, context);
        } else if ("PUT".equals(httpMethod)) {
          return handleUserProfile(effectiveUserId, body, headers, context);
        }
      }

      // GET /users/profile/{userId}
      if (normalizedPath.startsWith("/users/profile/") && "GET".equals(httpMethod)) {
        String pathUserId = normalizedPath.substring("/users/profile/".length());
        if (pathUserId == null || pathUserId.isBlank()) {
          return createErrorResponse(400, "Bad Request", "userId is required in path");
        }
        return handleGetUserProfile(pathUserId, context);
      }

      // GET /users?interested={true|false}&paid={true|false}
      if ("/users".equals(normalizedPath) && "GET".equals(httpMethod)) {
        if (userId == null || userId.isBlank()) {
          return createErrorResponse(401, "Unauthorized", "Authentication required");
        }
        String interested = queryParams.get("interested");
        String paid = queryParams.get("paid");
        return handleGetUsers(interested, paid, context);
      }

      // Other user endpoints require Cognito identity
      if (userId == null || userId.isBlank()) {
        return createErrorResponse(401, "Unauthorized", "Authentication required");
      }

      // Add more user endpoints here as needed
    }

    // Group endpoints (require authentication)
    // Supports: /groups/*, /v1/groups/*
    if (normalizedPath.startsWith("/groups")) {
      if (userId == null || userId.isBlank()) {
        return createErrorResponse(401, "Unauthorized", "Authentication required");
      }

      // GET /groups/{groupId}
      if (normalizedPath.matches("/groups/[^/]+") && "GET".equals(httpMethod)) {
        String groupId = normalizedPath.substring("/groups/".length());
        return handleGetGroup(groupId, context);
      }

      // GET
      // /groups?userId={userId}&experienceId={experienceId}&includeUsers={true|false}&notPaid={true|false}
      if ("/groups".equals(normalizedPath) && "GET".equals(httpMethod)) {
        String queryUserId = queryParams.get("userId");
        String experienceId = queryParams.get("experienceId");
        String includeUsers = queryParams.get("includeUsers");
        String notPaid = queryParams.get("notPaid");
        return handleGetGroups(queryUserId, experienceId, includeUsers, notPaid, context);
      }

      // PUT /groups/{groupId}
      if (normalizedPath.matches("/groups/[^/]+") && "PUT".equals(httpMethod)) {
        String groupId = normalizedPath.substring("/groups/".length());
        return handleCreateOrUpdateGroup(groupId, body, userId, context);
      }
    }

    // Experience endpoints (require authentication)
    // Supports: /experiences/*, /v1/experiences/*
    if (normalizedPath.startsWith("/experiences")) {
      if (userId == null || userId.isBlank()) {
        return createErrorResponse(401, "Unauthorized", "Authentication required");
      }

      // GET /experiences/{experienceId}/interested-users - Get users interested in an experience
      if (normalizedPath.contains("/interested-users") && "GET".equals(httpMethod)) {
        String experienceId = null;
        // Try path parameters first
        if (pathParameters != null && pathParameters.containsKey("experienceId")) {
          experienceId = pathParameters.get("experienceId");
        }

        // If not in path parameters, extract from path
        if (experienceId == null || experienceId.isBlank()) {
          String pathWithSuffix = normalizedPath.substring("/experiences/".length());
          int interestedUsersIndex = pathWithSuffix.indexOf("/interested-users");
          if (interestedUsersIndex > 0) {
            experienceId = pathWithSuffix.substring(0, interestedUsersIndex);
          }
        }

        if (experienceId != null && !experienceId.isBlank()) {
          return handleGetInterestedUsers(experienceId, context);
        }
      }

      // GET /experiences/{experienceId}/attended-users - Get users who attended (paid) for an
      // experience
      if (normalizedPath.contains("/attended-users") && "GET".equals(httpMethod)) {
        String experienceId = null;
        // Try path parameters first
        if (pathParameters != null && pathParameters.containsKey("experienceId")) {
          experienceId = pathParameters.get("experienceId");
        }

        // If not in path parameters, extract from path
        if (experienceId == null || experienceId.isBlank()) {
          String pathWithSuffix = normalizedPath.substring("/experiences/".length());
          int attendedUsersIndex = pathWithSuffix.indexOf("/attended-users");
          if (attendedUsersIndex > 0) {
            experienceId = pathWithSuffix.substring(0, attendedUsersIndex);
          }
        }

        if (experienceId != null && !experienceId.isBlank()) {
          return handleGetAttendedUsers(experienceId, context);
        }
      }

      // GET /experiences/{experienceId}
      if (normalizedPath.startsWith("/experiences/") && "GET".equals(httpMethod)) {
        // Try to get experienceId from path parameters first (from API Gateway)
        String experienceId = null;
        if (pathParameters != null && pathParameters.containsKey("experienceId")) {
          experienceId = pathParameters.get("experienceId");
        }

        // If not in path parameters, extract from path
        if (experienceId == null || experienceId.isBlank()) {
          String pathAfterExperiences = normalizedPath.substring("/experiences/".length());
          if (pathAfterExperiences != null && !pathAfterExperiences.isBlank()) {
            // Remove trailing slash if present and check if it's not the interested-users endpoint
            if (pathAfterExperiences.endsWith("/interested-users")) {
              // This is handled by the interested-users endpoint below
              // Skip here
            } else {
              experienceId =
                  pathAfterExperiences.endsWith("/")
                      ? pathAfterExperiences.substring(0, pathAfterExperiences.length() - 1)
                      : pathAfterExperiences;
            }
          }
        }

        // Only handle if we have a valid experienceId (not interested-users endpoint)
        if (experienceId != null && !experienceId.isBlank() && !experienceId.contains("/")) {
          return handleGetExperience(experienceId, context);
        }
      }

      // GET
      // /experiences?userId={userId}&groupId={groupId}&lat={lat}&lon={lon}&radius={radius}&interested={true}&past={true}&upcoming={true}
      // GET /experiences - Returns all users interested in upcoming experiences (when no userId
      // provided)
      if ("/experiences".equals(normalizedPath) && "GET".equals(httpMethod)) {
        String queryExperienceId = queryParams.get("experienceId");

        // If experienceId is provided as query parameter, get that specific experience
        if (queryExperienceId != null && !queryExperienceId.isBlank()) {
          // Remove quotes if present (from query string parsing)
          String cleanedExperienceId = queryExperienceId.trim();
          if ((cleanedExperienceId.startsWith("\"") && cleanedExperienceId.endsWith("\""))
              || (cleanedExperienceId.startsWith("'") && cleanedExperienceId.endsWith("'"))) {
            cleanedExperienceId =
                cleanedExperienceId.substring(1, cleanedExperienceId.length() - 1);
          }
          // Remove EXPERIENCE# prefix if present
          String normalizedExperienceId =
              cleanedExperienceId.startsWith("EXPERIENCE#")
                  ? cleanedExperienceId.replace("EXPERIENCE#", "")
                  : cleanedExperienceId;

          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: GET /experiences with experienceId query param: "
                        + queryExperienceId
                        + " (normalized: "
                        + normalizedExperienceId
                        + ")");
          }

          return handleGetExperience(normalizedExperienceId, context);
        }

        String queryUserId = queryParams.get("userId");
        String queryGroupId = queryParams.get("groupId");
        String lat = queryParams.get("lat");
        String lon = queryParams.get("lon");
        String radius = queryParams.get("radius");
        String interested = queryParams.get("interested");
        String past = queryParams.get("past");
        String upcoming = queryParams.get("upcoming");

        // Fix: If query parameters are malformed (e.g., userId contains "$past=true"), extract them
        if (queryUserId != null && queryUserId.contains("$")) {
          // Extract parameters from malformed userId string
          // Format: "userId"$past=true or userId$past=true
          String[] parts = queryUserId.split("\\$");
          if (parts.length > 0) {
            // Clean the userId part
            String cleanedUserId = parts[0].trim();
            // Remove quotes if present
            if ((cleanedUserId.startsWith("\"") && cleanedUserId.endsWith("\""))
                || (cleanedUserId.startsWith("'") && cleanedUserId.endsWith("'"))) {
              cleanedUserId = cleanedUserId.substring(1, cleanedUserId.length() - 1);
            }
            queryUserId = cleanedUserId;

            // Extract past/upcoming/interested from the malformed string
            for (int i = 1; i < parts.length; i++) {
              String paramPart = parts[i];
              if (paramPart.startsWith("past=")) {
                past = paramPart.substring(5); // Remove "past=" prefix
              } else if (paramPart.startsWith("upcoming=")) {
                upcoming = paramPart.substring(9); // Remove "upcoming=" prefix
              } else if (paramPart.startsWith("interested=")) {
                interested = paramPart.substring(11); // Remove "interested=" prefix
              }
            }

            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: Extracted from malformed userId - userId=["
                          + queryUserId
                          + "], past=["
                          + past
                          + "], upcoming=["
                          + upcoming
                          + "], interested=["
                          + interested
                          + "]");
            }
          }
        }

        // Also try multiValueQueryStringParameters as fallback (API Gateway sometimes uses this)
        if (multiValueQueryParams != null && !multiValueQueryParams.isEmpty()) {
          Map<String, List<String>> multiParams = multiValueQueryParams;
          if (past == null
              && multiParams.containsKey("past")
              && !multiParams.get("past").isEmpty()) {
            past = multiParams.get("past").get(0);
            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: Extracted past from multiValueQueryStringParameters: [" + past + "]");
            }
          }
          if (upcoming == null
              && multiParams.containsKey("upcoming")
              && !multiParams.get("upcoming").isEmpty()) {
            upcoming = multiParams.get("upcoming").get(0);
          }
          if (interested == null
              && multiParams.containsKey("interested")
              && !multiParams.get("interested").isEmpty()) {
            interested = multiParams.get("interested").get(0);
          }
          if (queryUserId == null
              && multiParams.containsKey("userId")
              && !multiParams.get("userId").isEmpty()) {
            queryUserId = multiParams.get("userId").get(0);
          }
        }

        // Clean up userId - remove quotes if present
        if (queryUserId != null) {
          queryUserId = queryUserId.trim();
          if ((queryUserId.startsWith("\"") && queryUserId.endsWith("\""))
              || (queryUserId.startsWith("'") && queryUserId.endsWith("'"))) {
            queryUserId = queryUserId.substring(1, queryUserId.length() - 1);
          }
        }

        // Debug: Log all query parameters for troubleshooting
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Query parameters - userId=["
                      + queryUserId
                      + "], past=["
                      + past
                      + "], interested=["
                      + interested
                      + "], upcoming=["
                      + upcoming
                      + "]");
          context
              .getLogger()
              .log(
                  "DEBUG: Location params - lat=["
                      + lat
                      + "], lon=["
                      + lon
                      + "], radius=["
                      + radius
                      + "]");
        }

        // If past=true or upcoming=true or interested=true, use authenticated userId if queryUserId
        // not provided
        boolean needsUserId =
            (past != null && ("true".equalsIgnoreCase(past) || "1".equals(past)))
                || (upcoming != null && ("true".equalsIgnoreCase(upcoming) || "1".equals(upcoming)))
                || (interested != null
                    && ("true".equalsIgnoreCase(interested) || "1".equals(interested)));

        if (needsUserId && queryUserId == null && userId != null && !userId.isBlank()) {
          queryUserId = userId; // Use authenticated user's ID
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: Using authenticated userId for past/interested/upcoming filter: "
                        + queryUserId);
          }
        }

        // If no userId and no location params, return all users interested in upcoming experiences
        if (queryUserId == null && lat == null && lon == null && queryGroupId == null) {
          return handleGetAllInterestedUsers(context);
        }

        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: GET /experiences - lat="
                      + lat
                      + ", lon="
                      + lon
                      + ", radius="
                      + radius
                      + ", userId="
                      + queryUserId);
        }

        return handleGetExperiences(
            queryUserId, queryGroupId, lat, lon, radius, interested, past, upcoming, context);
      }

      // PUT /experiences/{experienceId}
      if (normalizedPath.startsWith("/experiences") && "PUT".equals(httpMethod)) {
        // Try to get experienceId from path parameters first (from API Gateway)
        String experienceId = null;
        if (pathParameters != null && pathParameters.containsKey("experienceId")) {
          experienceId = pathParameters.get("experienceId");
        }

        // If not in path parameters, extract from path
        if (experienceId == null || experienceId.isBlank()) {
          // Handle both /experiences/{id} and /experiences/{id}/
          if (normalizedPath.equals("/experiences") || normalizedPath.equals("/experiences/")) {
            // Path is just /experiences or /experiences/ - missing experienceId
            return createErrorResponse(
                400,
                "Bad Request",
                "experienceId is required in the path. Use /v1/experiences/{experienceId}");
          }

          // Extract experienceId from path
          String pathAfterExperiences = normalizedPath.substring("/experiences/".length());
          if (pathAfterExperiences != null && !pathAfterExperiences.isBlank()) {
            // Remove trailing slash if present
            experienceId =
                pathAfterExperiences.endsWith("/")
                    ? pathAfterExperiences.substring(0, pathAfterExperiences.length() - 1)
                    : pathAfterExperiences;
          }
        }

        // Validate experienceId is present
        if (experienceId == null || experienceId.isBlank()) {
          return createErrorResponse(
              400,
              "Bad Request",
              "experienceId is required in the path. Use /v1/experiences/{experienceId}");
        }

        return handleCreateOrUpdateExperience(experienceId, body, userId, context);
      }
    }

    // User-Experience endpoints (require authentication)
    // Supports: /users/{userId}/experiences/{experienceId}/interest,
    // /v1/users/{userId}/experiences/{experienceId}/interest
    if (normalizedPath.startsWith("/users/")
        && normalizedPath.contains("/experiences/")
        && normalizedPath.endsWith("/interest")) {
      if (userId == null || userId.isBlank()) {
        return createErrorResponse(401, "Unauthorized", "Authentication required");
      }

      // PUT /users/{userId}/experiences/{experienceId}/interest - Mark user as interested in
      // experience
      if ("PUT".equals(httpMethod)) {
        // Extract userId and experienceId from path like
        // /users/{userId}/experiences/{experienceId}/interest
        String[] pathParts = normalizedPath.split("/");
        if (context != null && context.getLogger() != null) {
          context.getLogger().log("DEBUG: normalizedPath=" + normalizedPath);
          context.getLogger().log("DEBUG: pathParts.length=" + pathParts.length);
          for (int i = 0; i < pathParts.length; i++) {
            context.getLogger().log("DEBUG: pathParts[" + i + "]=" + pathParts[i]);
          }
          context.getLogger().log("DEBUG: authenticated userId=" + userId);
        }
        // Try to get userId and experienceId from path parameters first (if API Gateway provides
        // them)
        String pathUserId = pathParameters.get("userId");
        String experienceId = pathParameters.get("experienceId");

        // If not in path parameters, parse from path
        if (pathUserId == null || experienceId == null) {
          if (pathParts.length >= 5
              && "users".equals(pathParts[1])
              && "experiences".equals(pathParts[3])) {
            pathUserId = pathParts[2];
            experienceId = pathParts[4];
          } else {
            return createErrorResponse(
                400,
                "Bad Request",
                "Invalid path format. Expected /users/{userId}/experiences/{experienceId}/interest");
          }
        }

        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: pathUserId=["
                      + pathUserId
                      + "], length="
                      + (pathUserId != null ? pathUserId.length() : "null"));
          context
              .getLogger()
              .log(
                  "DEBUG: authenticated userId=["
                      + userId
                      + "], length="
                      + (userId != null ? userId.length() : "null"));
          context.getLogger().log("DEBUG: experienceId=" + experienceId);
          context
              .getLogger()
              .log(
                  "DEBUG: pathUserId.equals(userId)="
                      + (pathUserId != null && userId != null
                          ? pathUserId.equals(userId)
                          : "null check"));
          if (pathUserId != null && userId != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: pathUserId.trim().equals(userId.trim())="
                        + pathUserId.trim().equals(userId.trim()));
          }
        }

        // Verify the userId in path matches authenticated user (trim whitespace for safety)
        String trimmedPathUserId = pathUserId != null ? pathUserId.trim() : "";
        String trimmedUserId = userId != null ? userId.trim() : "";
        if (!trimmedPathUserId.equals(trimmedUserId)) {
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: UserId mismatch after trim - pathUserId=['"
                        + trimmedPathUserId
                        + "'], authenticated userId=['"
                        + trimmedUserId
                        + "']");
          }
          return createErrorResponse(
              403, "Forbidden", "You can only mark interest for your own account");
        }

        return handleMarkUserInterest(userId, experienceId, body, context);
      }
    }

    // User-Experience payment endpoints (require authentication)
    // Supports: /users/{userId}/experiences/{experienceId}/payment,
    // /v1/users/{userId}/experiences/{experienceId}/payment
    if (normalizedPath.startsWith("/users/")
        && normalizedPath.contains("/experiences/")
        && normalizedPath.endsWith("/payment")) {
      if (userId == null || userId.isBlank()) {
        return createErrorResponse(401, "Unauthorized", "Authentication required");
      }

      // PUT /users/{userId}/experiences/{experienceId}/payment - Mark user as paid for experience
      if ("PUT".equals(httpMethod)) {
        // Extract userId and experienceId from path like
        // /users/{userId}/experiences/{experienceId}/payment
        String[] pathParts = normalizedPath.split("/");
        if (context != null && context.getLogger() != null) {
          context.getLogger().log("DEBUG: normalizedPath=" + normalizedPath);
          context.getLogger().log("DEBUG: pathParts.length=" + pathParts.length);
          for (int i = 0; i < pathParts.length; i++) {
            context.getLogger().log("DEBUG: pathParts[" + i + "]=" + pathParts[i]);
          }
          context.getLogger().log("DEBUG: authenticated userId=" + userId);
        }
        // Try to get userId and experienceId from path parameters first (if API Gateway provides
        // them)
        String pathUserId = pathParameters.get("userId");
        String experienceId = pathParameters.get("experienceId");

        // If not in path parameters, parse from path
        if (pathUserId == null || experienceId == null) {
          if (pathParts.length >= 5
              && "users".equals(pathParts[1])
              && "experiences".equals(pathParts[3])) {
            pathUserId = pathParts[2];
            experienceId = pathParts[4];
          } else {
            return createErrorResponse(
                400,
                "Bad Request",
                "Invalid path format. Expected /users/{userId}/experiences/{experienceId}/payment");
          }
        }

        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: pathUserId=["
                      + pathUserId
                      + "], length="
                      + (pathUserId != null ? pathUserId.length() : "null"));
          context
              .getLogger()
              .log(
                  "DEBUG: authenticated userId=["
                      + userId
                      + "], length="
                      + (userId != null ? userId.length() : "null"));
          context.getLogger().log("DEBUG: experienceId=" + experienceId);
        }

        // Verify the userId in path matches authenticated user (trim whitespace for safety)
        String trimmedPathUserId = pathUserId != null ? pathUserId.trim() : "";
        String trimmedUserId = userId != null ? userId.trim() : "";
        if (!trimmedPathUserId.equals(trimmedUserId)) {
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: UserId mismatch after trim - pathUserId=['"
                        + trimmedPathUserId
                        + "'], authenticated userId=['"
                        + trimmedUserId
                        + "']");
          }
          return createErrorResponse(
              403, "Forbidden", "You can only mark payment for your own account");
        }

        return handleMarkUserPayment(userId, experienceId, body, context);
      }
    }

    // 404 for unknown paths
    return createErrorResponse(404, "Not Found", "Endpoint not found: " + path);
  }

  /**
   * Normalizes the path by removing version prefix if present. Converts /v1/health -> /health,
   * /v1/api/auth/me -> /api/auth/me
   */
  private String normalizePath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    // Remove /v1 prefix if present
    if (path.startsWith("/v1/")) {
      return path.substring(3); // Remove "/v1"
    } else if (path.equals("/v1")) {
      return "/";
    }
    return path;
  }

  /** Handle GET /api/auth/me - Get current user information. */
  private APIGatewayProxyResponseEvent handleGetMe(String userId, String cognitoData) {
    try {
      Map<String, Object> userInfo = new HashMap<>();
      userInfo.put("userId", userId);
      userInfo.put("hasClaims", cognitoData != null && !cognitoData.isBlank());
      if (cognitoData != null && !cognitoData.isBlank()) {
        userInfo.put("claimsAvailable", true);
        // Note: cognitoData is base64-encoded JSON, decode on client if needed
      }

      Map<String, Object> responseData =
          Map.of(
              "success",
              true,
              "message",
              "User information retrieved successfully",
              "data",
              userInfo,
              "timestamp",
              java.time.Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      return createErrorResponse(500, "Error processing request", e.getMessage());
    }
  }

  /** Handle GET /api/auth/status - Check authentication status. */
  private APIGatewayProxyResponseEvent handleGetAuthStatus(String userId) {
    try {
      Map<String, Object> statusData =
          Map.of(
              "success",
              true,
              "message",
              "Authentication status",
              "data",
              Map.of("authenticated", true, "userId", userId),
              "timestamp",
              java.time.Instant.now().toString());

      return createSuccessResponse(200, statusData, "application/json");
    } catch (Exception e) {
      return createErrorResponse(500, "Error processing request", e.getMessage());
    }
  }

  /** Handle GET /users/profile - Get user profile by userId. */
  private APIGatewayProxyResponseEvent handleGetUserProfile(String userId, Context context) {
    try {
      // Find user profile in DynamoDB
      Optional<UserProfile> profileOpt = findByUserId(userId, context);

      if (profileOpt.isEmpty()) {
        return createErrorResponse(
            404, "Not Found", "User profile not found for userId: " + userId);
      }

      UserProfile profile = profileOpt.get();

      // Convert to response
      UserProfileResponse response = toResponse(profile);

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "User profile retrieved successfully");
      responseData.put("data", response);
      responseData.put("timestamp", java.time.Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
        e.printStackTrace();
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /** Handle PUT /users/profile - Create or update user profile. */
  private APIGatewayProxyResponseEvent handleUserProfile(
      String userId, String body, Map<String, String> headers, Context context) {
    try {
      // Extract UserId from header if provided (for compatibility with UserController)
      String headerUserId = headers.get("UserId");
      String effectiveUserId =
          (headerUserId != null && !headerUserId.isBlank()) ? headerUserId : userId;

      if (effectiveUserId == null || effectiveUserId.isBlank()) {
        return createErrorResponse(400, "Bad Request", "User ID is required");
      }

      // Parse request body
      if (body == null || body.isBlank()) {
        return createErrorResponse(400, "Bad Request", "Request body is required");
      }

      UserProfileRequest request;
      try {
        request = objectMapper.readValue(body, UserProfileRequest.class);
      } catch (Exception e) {
        return createErrorResponse(
            400, "Bad Request", "Invalid JSON in request body: " + e.getMessage());
      }

      // Find existing profile or create new one
      Optional<UserProfile> existingProfile = findByUserId(effectiveUserId, context);
      UserProfile profile;
      if (existingProfile.isPresent()) {
        profile = existingProfile.get();
      } else {
        profile = new UserProfile(effectiveUserId);
      }

      // Ensure userId is set (in case updateProfileFromRequest clears it)
      if (profile.getUserId() == null || profile.getUserId().isBlank()) {
        profile.setUserId(effectiveUserId);
      }

      // Update profile fields from request
      updateProfileFromRequest(profile, request);

      // Ensure userId is still set after update (safety check)
      if (profile.getUserId() == null || profile.getUserId().isBlank()) {
        profile.setUserId(effectiveUserId);
      }

      // Save to DynamoDB
      saveProfile(profile, context);

      // Convert to response
      UserProfileResponse response = toResponse(profile);

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "User profile saved successfully");
      responseData.put("data", response);
      responseData.put("timestamp", java.time.Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
        e.printStackTrace();
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /** Find an active user profile by userId. */
  private Optional<UserProfile> findByUserId(String userId, Context context) {
    try {
      Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
      expressionAttributeValues.put(":userId", AttributeValue.builder().s(userId).build());

      QueryRequest queryRequest =
          QueryRequest.builder()
              .tableName(TABLE_NAME)
              .keyConditionExpression("pk = :userId")
              .expressionAttributeValues(expressionAttributeValues)
              .scanIndexForward(false) // Sort descending (latest first)
              .limit(1) // Get only the latest profile
              .build();

      QueryResponse response = dynamoDbClient.query(queryRequest);

      if (response.items().isEmpty()) {
        return Optional.empty();
      }

      UserProfile profile = fromAttributeMap(response.items().get(0));
      // Filter out deleted profiles
      if (profile.isDeleted()) {
        return Optional.empty();
      }
      return Optional.of(profile);
    } catch (Exception e) {
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Error finding user profile: " + e.getMessage());
      }
      return Optional.empty();
    }
  }

  /** Save or update a user profile in DynamoDB. */
  private void saveProfile(UserProfile profile, Context context) {
    // Validate userId is set
    if (profile.getUserId() == null || profile.getUserId().isBlank()) {
      throw new IllegalArgumentException("Cannot save profile: userId is required");
    }

    profile.setUpdatedAt(Instant.now());
    if (profile.getCreatedAt() == null) {
      profile.setCreatedAt(Instant.now());
    }

    // Check if profile already exists
    Optional<UserProfile> existing = findByUserId(profile.getUserId(), context);
    if (existing.isPresent()) {
      // Update existing profile - preserve original createdAt
      UserProfile existingProfile = existing.get();
      profile.setCreatedAt(existingProfile.getCreatedAt());
      updateProfile(profile, context);
    } else {
      // Create new profile
      createProfile(profile, context);
    }
  }

  /** Create a new user profile in DynamoDB. */
  private void createProfile(UserProfile profile, Context context) {
    try {
      Map<String, AttributeValue> item = toAttributeMap(profile);
      PutItemRequest putRequest = PutItemRequest.builder().tableName(TABLE_NAME).item(item).build();
      dynamoDbClient.putItem(putRequest);
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Created new user profile for userId: " + profile.getUserId());
      }
    } catch (Exception e) {
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Error creating user profile: " + e.getMessage());
      }
      throw e;
    }
  }

  /** Update an existing user profile in DynamoDB. */
  private void updateProfile(UserProfile profile, Context context) {
    Map<String, AttributeValue> key =
        buildCompositeKey(profile.getUserId(), profile.getCreatedAt());

    Map<String, String> expressionAttributeNames = new HashMap<>();
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

    StringBuilder updateExpression = new StringBuilder("SET #updatedAt = :updatedAt");
    expressionAttributeNames.put("#updatedAt", "updatedAt");
    expressionAttributeValues.put(
        ":updatedAt", AttributeValue.builder().s(profile.getUpdatedAt().toString()).build());

    // Add optional fields to update
    if (profile.getDateOfBirth() != null) {
      updateExpression.append(", #dateOfBirth = :dateOfBirth");
      expressionAttributeNames.put("#dateOfBirth", "dateOfBirth");
      expressionAttributeValues.put(
          ":dateOfBirth", AttributeValue.builder().s(profile.getDateOfBirth().toString()).build());
    }
    if (profile.getAddress() != null) {
      updateExpression.append(", #address = :address");
      expressionAttributeNames.put("#address", "address");
      expressionAttributeValues.put(
          ":address", AttributeValue.builder().s(profile.getAddress()).build());
    }
    if (profile.getCity() != null) {
      updateExpression.append(", #city = :city");
      expressionAttributeNames.put("#city", "city");
      expressionAttributeValues.put(":city", AttributeValue.builder().s(profile.getCity()).build());
    }
    if (profile.getState() != null) {
      updateExpression.append(", #state = :state");
      expressionAttributeNames.put("#state", "state");
      expressionAttributeValues.put(
          ":state", AttributeValue.builder().s(profile.getState()).build());
    }
    if (profile.getZipCode() != null) {
      updateExpression.append(", #zipCode = :zipCode");
      expressionAttributeNames.put("#zipCode", "zipCode");
      expressionAttributeValues.put(
          ":zipCode", AttributeValue.builder().s(profile.getZipCode()).build());
    }
    if (profile.getCountry() != null) {
      updateExpression.append(", #country = :country");
      expressionAttributeNames.put("#country", "country");
      expressionAttributeValues.put(
          ":country", AttributeValue.builder().s(profile.getCountry()).build());
    }
    if (profile.getGender() != null) {
      updateExpression.append(", #gender = :gender");
      expressionAttributeNames.put("#gender", "gender");
      expressionAttributeValues.put(
          ":gender", AttributeValue.builder().s(profile.getGender()).build());
    }
    if (profile.getProfession() != null) {
      updateExpression.append(", #profession = :profession");
      expressionAttributeNames.put("#profession", "profession");
      expressionAttributeValues.put(
          ":profession", AttributeValue.builder().s(profile.getProfession()).build());
    }
    if (profile.getCompany() != null) {
      updateExpression.append(", #company = :company");
      expressionAttributeNames.put("#company", "company");
      expressionAttributeValues.put(
          ":company", AttributeValue.builder().s(profile.getCompany()).build());
    }
    if (profile.getBio() != null) {
      updateExpression.append(", #bio = :bio");
      expressionAttributeNames.put("#bio", "bio");
      expressionAttributeValues.put(":bio", AttributeValue.builder().s(profile.getBio()).build());
    }
    if (profile.getPhoneNumber() != null) {
      updateExpression.append(", #phoneNumber = :phoneNumber");
      expressionAttributeNames.put("#phoneNumber", "phoneNumber");
      expressionAttributeValues.put(
          ":phoneNumber", AttributeValue.builder().s(profile.getPhoneNumber()).build());
    }
    if (profile.getStatus() != null) {
      updateExpression.append(", #status = :status");
      expressionAttributeNames.put("#status", "status");
      expressionAttributeValues.put(
          ":status", AttributeValue.builder().s(profile.getStatus().getValue()).build());
    }

    UpdateItemRequest.Builder updateRequestBuilder =
        UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(key)
            .updateExpression(updateExpression.toString())
            .expressionAttributeValues(expressionAttributeValues);

    if (!expressionAttributeNames.isEmpty()) {
      updateRequestBuilder.expressionAttributeNames(expressionAttributeNames);
    }

    try {
      dynamoDbClient.updateItem(updateRequestBuilder.build());
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Updated user profile for userId: " + profile.getUserId());
      }
    } catch (Exception e) {
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Error updating user profile: " + e.getMessage());
      }
      throw e;
    }
  }

  /** Build composite key for DynamoDB operations. */
  private Map<String, AttributeValue> buildCompositeKey(String userId, Instant createdAt) {
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("pk", AttributeValue.builder().s(userId).build());
    key.put("sk", AttributeValue.builder().s(createdAt.toString()).build());
    return key;
  }

  /** Convert UserProfile to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(UserProfile profile) {
    Map<String, AttributeValue> item = new HashMap<>();

    // Validate required fields
    if (profile.getUserId() == null || profile.getUserId().isBlank()) {
      throw new IllegalArgumentException("UserProfile userId cannot be null or blank");
    }
    if (profile.getCreatedAt() == null) {
      throw new IllegalArgumentException("UserProfile createdAt cannot be null");
    }

    // Composite key: pk (partition) = userId, sk (sort) = createdAt
    item.put("pk", AttributeValue.builder().s(profile.getUserId()).build());
    item.put("sk", AttributeValue.builder().s(profile.getCreatedAt().toString()).build());
    // Also store userId as regular attribute for reference
    item.put("userId", AttributeValue.builder().s(profile.getUserId()).build());

    if (profile.getDateOfBirth() != null) {
      item.put(
          "dateOfBirth", AttributeValue.builder().s(profile.getDateOfBirth().toString()).build());
    }
    if (profile.getAddress() != null) {
      item.put("address", AttributeValue.builder().s(profile.getAddress()).build());
    }
    if (profile.getCity() != null) {
      item.put("city", AttributeValue.builder().s(profile.getCity()).build());
    }
    if (profile.getState() != null) {
      item.put("state", AttributeValue.builder().s(profile.getState()).build());
    }
    if (profile.getZipCode() != null) {
      item.put("zipCode", AttributeValue.builder().s(profile.getZipCode()).build());
    }
    if (profile.getCountry() != null) {
      item.put("country", AttributeValue.builder().s(profile.getCountry()).build());
    }
    if (profile.getLatitude() != null) {
      item.put("latitude", AttributeValue.builder().n(profile.getLatitude().toString()).build());
    }
    if (profile.getLongitude() != null) {
      item.put("longitude", AttributeValue.builder().n(profile.getLongitude().toString()).build());
    }
    if (profile.getGender() != null) {
      item.put("gender", AttributeValue.builder().s(profile.getGender()).build());
    }
    if (profile.getProfession() != null) {
      item.put("profession", AttributeValue.builder().s(profile.getProfession()).build());
    }
    if (profile.getCompany() != null) {
      item.put("company", AttributeValue.builder().s(profile.getCompany()).build());
    }
    if (profile.getBio() != null) {
      item.put("bio", AttributeValue.builder().s(profile.getBio()).build());
    }
    if (profile.getPhoneNumber() != null) {
      item.put("phoneNumber", AttributeValue.builder().s(profile.getPhoneNumber()).build());
    }
    if (profile.getStatus() != null) {
      item.put("status", AttributeValue.builder().s(profile.getStatus().getValue()).build());
    }
    if (profile.getUpdatedAt() != null) {
      item.put("updatedAt", AttributeValue.builder().s(profile.getUpdatedAt().toString()).build());
    }

    return item;
  }

  /** Convert DynamoDB AttributeValue map to UserProfile. */
  private UserProfile fromAttributeMap(Map<String, AttributeValue> item) {
    UserProfile profile = new UserProfile();

    if (item.containsKey("userId")) {
      profile.setUserId(item.get("userId").s());
    }
    if (item.containsKey("dateOfBirth")) {
      profile.setDateOfBirth(LocalDate.parse(item.get("dateOfBirth").s()));
    }
    if (item.containsKey("address")) {
      profile.setAddress(item.get("address").s());
    }
    if (item.containsKey("city")) {
      profile.setCity(item.get("city").s());
    }
    if (item.containsKey("state")) {
      profile.setState(item.get("state").s());
    }
    if (item.containsKey("zipCode")) {
      profile.setZipCode(item.get("zipCode").s());
    }
    if (item.containsKey("country")) {
      profile.setCountry(item.get("country").s());
    }
    if (item.containsKey("latitude")) {
      profile.setLatitude(Double.parseDouble(item.get("latitude").n()));
    }
    if (item.containsKey("longitude")) {
      profile.setLongitude(Double.parseDouble(item.get("longitude").n()));
    }
    if (item.containsKey("gender")) {
      profile.setGender(item.get("gender").s());
    }
    if (item.containsKey("profession")) {
      profile.setProfession(item.get("profession").s());
    }
    if (item.containsKey("company")) {
      profile.setCompany(item.get("company").s());
    }
    if (item.containsKey("bio")) {
      profile.setBio(item.get("bio").s());
    }
    if (item.containsKey("phoneNumber")) {
      profile.setPhoneNumber(item.get("phoneNumber").s());
    }
    if (item.containsKey("status")) {
      profile.setStatus(UserStatus.fromValue(item.get("status").s()));
    } else {
      profile.setStatus(UserStatus.ACTIVE);
    }
    // Read createdAt from sk (sort key) or createdAt (for backward compatibility)
    if (item.containsKey("sk")) {
      profile.setCreatedAt(Instant.parse(item.get("sk").s()));
    } else if (item.containsKey("createdAt")) {
      profile.setCreatedAt(Instant.parse(item.get("createdAt").s()));
    }
    if (item.containsKey("updatedAt")) {
      profile.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return profile;
  }

  /** Update UserProfile entity from request DTO. */
  private void updateProfileFromRequest(UserProfile profile, UserProfileRequest request) {
    if (request.getDateOfBirth() != null) {
      profile.setDateOfBirth(request.getDateOfBirth());
    }
    if (request.getAddress() != null) {
      profile.setAddress(request.getAddress());
    }
    if (request.getCity() != null) {
      profile.setCity(request.getCity());
    }
    if (request.getState() != null) {
      profile.setState(request.getState());
    }
    if (request.getZipCode() != null) {
      profile.setZipCode(request.getZipCode());
    }
    if (request.getCountry() != null) {
      profile.setCountry(request.getCountry());
    }
    if (request.getLatitude() != null) {
      profile.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      profile.setLongitude(request.getLongitude());
    }
    if (request.getGender() != null) {
      profile.setGender(request.getGender());
    }
    if (request.getProfession() != null) {
      profile.setProfession(request.getProfession());
    }
    if (request.getCompany() != null) {
      profile.setCompany(request.getCompany());
    }
    if (request.getBio() != null) {
      profile.setBio(request.getBio());
    }
    if (request.getPhoneNumber() != null) {
      profile.setPhoneNumber(request.getPhoneNumber());
    }
  }

  /** Convert UserProfile entity to response DTO. */
  private UserProfileResponse toResponse(UserProfile profile) {
    UserProfileResponse response = new UserProfileResponse();
    response.setUserId(profile.getUserId());
    response.setDateOfBirth(profile.getDateOfBirth());
    response.setAddress(profile.getAddress());
    response.setCity(profile.getCity());
    response.setState(profile.getState());
    response.setZipCode(profile.getZipCode());
    response.setCountry(profile.getCountry());
    response.setLatitude(profile.getLatitude());
    response.setLongitude(profile.getLongitude());
    response.setGender(profile.getGender());
    response.setProfession(profile.getProfession());
    response.setCompany(profile.getCompany());
    response.setBio(profile.getBio());
    response.setPhoneNumber(profile.getPhoneNumber());
    response.setStatus(profile.getStatus());
    response.setCreatedAt(profile.getCreatedAt());
    response.setUpdatedAt(profile.getUpdatedAt());
    return response;
  }

  /**
   * Handle GET /users?interested={true|false}&paid={true|false} - Get users with experiences
   * filtered by interest and payment status.
   */
  private APIGatewayProxyResponseEvent handleGetUsers(
      String interested, String paid, Context context) {
    try {
      List<Map<String, Object>> users = new ArrayList<>();

      // Query UserExperience table filtered by status
      if ("true".equalsIgnoreCase(interested)) {
        // Get all users with INTERESTED status experiences
        // Note: This is a simplified implementation - in production, you might want to aggregate
        // by userId
        List<UserExperience> userExperiences =
            userExperienceDao.findByUserIdAndStatus(
                "USER#", UserExperienceStatus.INTERESTED); // Placeholder - needs proper userId
        // Convert to response format
        for (UserExperience ue : userExperiences) {
          Map<String, Object> userData = new HashMap<>();
          userData.put("userId", ue.getUserId());
          userData.put("experienceId", ue.getExperienceId());
          userData.put("interestScore", ue.getInterestScore());
          users.add(userData);
        }
      } else if ("true".equalsIgnoreCase(paid)) {
        // Get all users with PAID status experiences
        List<UserExperience> userExperiences =
            userExperienceDao.findByUserIdAndStatus(
                "USER#", UserExperienceStatus.PAID); // Placeholder - needs proper userId
        for (UserExperience ue : userExperiences) {
          Map<String, Object> userData = new HashMap<>();
          userData.put("userId", ue.getUserId());
          userData.put("experienceId", ue.getExperienceId());
          userData.put("paymentDetails", ue.getPaymentDetails());
          users.add(userData);
        }
      }

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Users retrieved successfully");
      responseData.put("data", users);
      responseData.put("timestamp", Instant.now().toString());
      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /** Handle GET /groups/{groupId} - Get group details with users and optional experience. */
  private APIGatewayProxyResponseEvent handleGetGroup(String groupId, Context context) {
    try {
      Optional<Group> groupOpt = groupDao.findByGroupId(groupId);

      if (groupOpt.isEmpty()) {
        return createErrorResponse(404, "Not Found", "Group not found: " + groupId);
      }

      Group group = groupOpt.get();

      // Get related experiences for this group
      List<GroupExperience> groupExperiences = groupExperienceDao.findByGroupId(group.getGroupId());
      List<String> experienceIds =
          groupExperiences.stream()
              .map(GroupExperience::getExperienceId)
              .collect(Collectors.toList());

      // Build response
      Map<String, Object> groupData = new HashMap<>();
      groupData.put("groupId", group.getGroupId());
      groupData.put("groupName", group.getGroupName());
      groupData.put("description", group.getDescription());
      groupData.put("status", group.getStatus());
      groupData.put("memberUserIds", group.getMemberUserIds());
      groupData.put("experienceIds", experienceIds);
      groupData.put("createdAt", group.getCreatedAt());
      groupData.put("updatedAt", group.getUpdatedAt());

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Group retrieved successfully");
      responseData.put("data", groupData);
      responseData.put("timestamp", Instant.now().toString());
      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle GET
   * /groups?userId={userId}&experienceId={experienceId}&includeUsers={true|false}&notPaid={true|false}
   * - Get groups filtered by user or experience.
   *
   * <p>For each group, includes a mapping of experiences to users in the group who are attending
   * those experiences (or users who haven't paid if notPaid=true).
   *
   * <p>If includeUsers=true and userId is provided, returns all unique users from all groups the
   * user belongs to.
   *
   * <p>If notPaid=true and experienceId is provided, returns users in the group who have NOT paid
   * for the experience.
   */
  private APIGatewayProxyResponseEvent handleGetGroups(
      String userId, String experienceId, String includeUsers, String notPaid, Context context) {
    try {
      List<Group> groups = new ArrayList<>();

      if (userId != null && !userId.isBlank()) {
        // Query groups by userId using GSI1
        groups = groupDao.findByUserId(userId);
      } else if (experienceId != null && !experienceId.isBlank()) {
        // Normalize experienceId - remove EXPERIENCE# prefix if present
        // Also remove surrounding quotes if present (from query string parsing)
        String cleanedExperienceId = experienceId.trim();
        if ((cleanedExperienceId.startsWith("\"") && cleanedExperienceId.endsWith("\""))
            || (cleanedExperienceId.startsWith("'") && cleanedExperienceId.endsWith("'"))) {
          cleanedExperienceId = cleanedExperienceId.substring(1, cleanedExperienceId.length() - 1);
        }
        String normalizedExperienceId =
            cleanedExperienceId.startsWith("EXPERIENCE#")
                ? cleanedExperienceId.replace("EXPERIENCE#", "")
                : cleanedExperienceId;

        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Querying groups for experienceId: "
                      + experienceId
                      + " (normalized: "
                      + normalizedExperienceId
                      + ")");
        }

        // Query groups by experienceId using GroupExperience (now uses scan, not GSI1)
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: About to call groupExperienceDao.findByExperienceId with: "
                      + normalizedExperienceId);
        }
        List<GroupExperience> groupExperiences =
            groupExperienceDao.findByExperienceId(normalizedExperienceId);
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: groupExperienceDao.findByExperienceId returned "
                      + groupExperiences.size()
                      + " relationships");
        }
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Found "
                      + groupExperiences.size()
                      + " GroupExperience relationships for experienceId: "
                      + normalizedExperienceId);
          for (GroupExperience ge : groupExperiences) {
            context
                .getLogger()
                .log(
                    "DEBUG: GroupExperience - groupId: "
                        + ge.getGroupId()
                        + ", experienceId: "
                        + ge.getExperienceId());
          }
        }
        for (GroupExperience ge : groupExperiences) {
          Optional<Group> groupOpt = groupDao.findByGroupId(ge.getGroupId());
          if (groupOpt.isPresent()) {
            Group group = groupOpt.get();
            groups.add(group);
            if (context != null && context.getLogger() != null) {
              List<String> memberIds = group.getMemberUserIds();
              context
                  .getLogger()
                  .log(
                      "DEBUG: Added group: "
                          + group.getGroupId()
                          + ", memberUserIds: "
                          + (memberIds != null ? memberIds.toString() : "null")
                          + " (size: "
                          + (memberIds != null ? memberIds.size() : 0)
                          + ")");
            }
          } else {
            if (context != null && context.getLogger() != null) {
              context.getLogger().log("DEBUG: Group not found for groupId: " + ge.getGroupId());
            }
          }
        }
      }

      // If includeUsers=true and userId is provided, return all unique users from all groups
      if (includeUsers != null
          && ("true".equalsIgnoreCase(includeUsers) || "1".equals(includeUsers))
          && userId != null
          && !userId.isBlank()) {

        // Collect all unique user IDs from all groups the user belongs to
        java.util.Set<String> allUserIds = new java.util.HashSet<>();
        List<Map<String, Object>> groupSummaryList = new ArrayList<>();

        for (Group group : groups) {
          List<String> groupMemberIds =
              group.getMemberUserIds() != null ? group.getMemberUserIds() : new ArrayList<>();

          // Add all members from this group to the set
          if (groupMemberIds != null) {
            for (String memberId : groupMemberIds) {
              if (memberId != null && !memberId.isBlank()) {
                allUserIds.add(memberId.trim());
              }
            }
          }

          // Also add the creator if present
          if (group.getUserId() != null && !group.getUserId().isBlank()) {
            allUserIds.add(group.getUserId().trim());
          }

          // Add group summary for reference
          Map<String, Object> groupSummary = new HashMap<>();
          groupSummary.put("groupId", group.getGroupId());
          groupSummary.put("groupName", group.getGroupName());
          groupSummary.put("memberCount", groupMemberIds != null ? groupMemberIds.size() : 0);
          groupSummaryList.add(groupSummary);
        }

        // Convert set to sorted list
        List<String> allUsersList = new ArrayList<>(allUserIds);
        java.util.Collections.sort(allUsersList);

        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Found "
                      + allUsersList.size()
                      + " unique users across "
                      + groups.size()
                      + " groups for user: "
                      + userId);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Users retrieved successfully from all groups");
        responseData.put(
            "data",
            Map.of(
                "userId", userId,
                "totalUsers", allUsersList.size(),
                "totalGroups", groups.size(),
                "userIds", allUsersList,
                "groups", groupSummaryList));
        responseData.put("timestamp", Instant.now().toString());
        return createSuccessResponse(200, responseData, "application/json");
      }

      // Convert to response format with experience-attending users mapping
      List<Map<String, Object>> groupList = new ArrayList<>();
      for (Group group : groups) {
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("groupId", group.getGroupId());
        groupData.put("groupName", group.getGroupName());
        groupData.put("description", group.getDescription());
        groupData.put("status", group.getStatus());
        // Ensure memberUserIds is never null - use empty list if null
        List<String> memberUserIds = group.getMemberUserIds();
        if (memberUserIds == null) {
          memberUserIds = new ArrayList<>();
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "WARNING: Group "
                        + group.getGroupId()
                        + " has null memberUserIds, initializing as empty list");
          }
        }
        groupData.put("memberUserIds", memberUserIds);

        // Get experiences associated with this group
        List<GroupExperience> groupExperiences =
            groupExperienceDao.findByGroupId(group.getGroupId());
        List<String> groupMemberIds =
            group.getMemberUserIds() != null ? group.getMemberUserIds() : new ArrayList<>();

        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Group "
                      + group.getGroupId()
                      + " has "
                      + groupMemberIds.size()
                      + " members: "
                      + groupMemberIds);
        }

        // Build experience-attending users mapping
        List<Map<String, Object>> experiencesList = new ArrayList<>();
        for (GroupExperience ge : groupExperiences) {
          String expId = ge.getExperienceId();
          // Remove EXPERIENCE# prefix if present for querying
          String normalizedExpId =
              expId.startsWith("EXPERIENCE#") ? expId.replace("EXPERIENCE#", "") : expId;

          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log("DEBUG: Querying UserExperience for experienceId: " + normalizedExpId);
          }

          // Get all users for this experience
          List<UserExperience> userExperiences =
              userExperienceDao.findByExperienceId(normalizedExpId);

          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: Found "
                        + userExperiences.size()
                        + " UserExperience records for experienceId: "
                        + normalizedExpId);
            if (!userExperiences.isEmpty()) {
              List<String> userIds =
                  userExperiences.stream()
                      .map(UserExperience::getUserId)
                      .filter(u -> u != null)
                      .collect(Collectors.toList());
              context.getLogger().log("DEBUG: User IDs from UserExperience: " + userIds);
            }
          }

          // Check if we need to filter for unpaid users
          boolean filterNotPaid =
              notPaid != null && ("true".equalsIgnoreCase(notPaid) || "1".equals(notPaid));

          if (filterNotPaid) {
            // Filter to get users who HAVE paid (to calculate who hasn't paid)
            List<String> paidUserIds =
                userExperiences.stream()
                    .filter(
                        ue -> {
                          // Check if user has paid - prioritize paid=true, then paymentDetails,
                          // then status
                          if (ue.getPaid() != null && ue.getPaid()) {
                            return true;
                          }
                          if (ue.getPaymentDetails() != null) {
                            return true;
                          }
                          if (ue.getStatus() == UserExperience.UserExperienceStatus.PAID
                              || ue.getStatus() == UserExperience.UserExperienceStatus.ATTENDED) {
                            return true;
                          }
                          return false;
                        })
                    .map(UserExperience::getUserId)
                    .filter(u -> u != null)
                    .distinct()
                    .collect(Collectors.toList());

            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: Found "
                          + paidUserIds.size()
                          + " users who have paid for experience ["
                          + normalizedExpId
                          + "]");
            }

            // Calculate unpaid users: group members - paid users
            List<String> unpaidUserIds =
                groupMemberIds.stream()
                    .filter(
                        memberId -> {
                          if (memberId == null || memberId.isBlank()) {
                            return false;
                          }
                          String trimmedMemberId = memberId.trim();
                          // User hasn't paid if they're in the group but not in the paid list
                          return !paidUserIds.stream()
                              .anyMatch(
                                  paidUserId ->
                                      paidUserId != null
                                          && paidUserId.trim().equalsIgnoreCase(trimmedMemberId));
                        })
                    .distinct()
                    .collect(Collectors.toList());

            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: Found "
                          + unpaidUserIds.size()
                          + " users in group who have NOT paid for experience ["
                          + normalizedExpId
                          + "]");
            }

            Map<String, Object> experienceData = new HashMap<>();
            experienceData.put("experienceId", expId);
            experienceData.put("unpaidUserIds", unpaidUserIds);
            experienceData.put("paidUserIds", paidUserIds);
            experienceData.put("totalGroupMembers", groupMemberIds.size());
            experiencesList.add(experienceData);
          } else {
            // Original logic: filter to only include users who are members of this group AND have
            // paid
            // Use case-insensitive comparison and trim whitespace
            List<String> attendingUserIds =
                userExperiences.stream()
                    .filter(
                        ue -> {
                          // Check if user has paid - prioritize paid=true, then paymentDetails,
                          // then status
                          boolean hasPaid = false;
                          if (ue.getPaid() != null && ue.getPaid()) {
                            hasPaid = true;
                          } else if (ue.getPaymentDetails() != null) {
                            hasPaid = true;
                          } else if (ue.getStatus() == UserExperience.UserExperienceStatus.PAID
                              || ue.getStatus() == UserExperience.UserExperienceStatus.ATTENDED) {
                            hasPaid = true;
                          }
                          return hasPaid;
                        })
                    .map(UserExperience::getUserId)
                    .filter(
                        ueUserId -> {
                          if (ueUserId == null) {
                            return false;
                          }
                          String trimmedUeUserId = ueUserId.trim();
                          return groupMemberIds.stream()
                              .anyMatch(
                                  memberId ->
                                      memberId != null
                                          && memberId.trim().equalsIgnoreCase(trimmedUeUserId));
                        })
                    .distinct()
                    .collect(Collectors.toList());

            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: After filtering by group members and payment status, attendingUserIds: "
                          + attendingUserIds);
            }

            Map<String, Object> experienceData = new HashMap<>();
            experienceData.put("experienceId", expId);
            experienceData.put("attendingUserIds", attendingUserIds);
            experiencesList.add(experienceData);
          }
        }

        groupData.put("experiences", experiencesList);
        groupList.add(groupData);
      }

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Groups retrieved successfully");
      responseData.put("data", groupList);
      responseData.put("timestamp", Instant.now().toString());
      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle PUT /groups/{groupId} - Create, update group, add/remove users, add/remove experiences,
   * or delete group.
   *
   * <p>Operations:
   *
   * <ul>
   *   <li>Create: If group doesn't exist, create with userIds
   *   <li>Add users: {"action": "add", "userIds": ["user1", "user2"]}
   *   <li>Remove users: {"action": "remove", "userIds": ["user1", "user2"]}
   *   <li>Add to experience: {"action": "addExperience", "experienceIds": ["exp-001", "exp-002"]}
   *   <li>Remove from experience: {"action": "removeExperience", "experienceIds": ["exp-001"]}
   *   <li>Hard delete (permanent): {"action": "hardDelete"} or {"action": "permanentDelete"}
   *   <li>Soft delete (mark as deleted): {"action": "delete"} or {"status": "DELETED"}
   * </ul>
   */
  private APIGatewayProxyResponseEvent handleCreateOrUpdateGroup(
      String groupId, String body, String userId, Context context) {
    try {
      // Check if group exists
      Optional<Group> existingGroupOpt = groupDao.findByGroupId(groupId);

      // Handle delete operations
      if (body == null || body.isBlank()) {
        // Empty body - try to delete if exists
        if (existingGroupOpt.isEmpty()) {
          return createErrorResponse(404, "Not Found", "Group not found: " + groupId);
        }
        Group group = existingGroupOpt.get();
        // Verify user is a member or creator
        if (!isGroupMemberOrCreator(group, userId)) {
          return createErrorResponse(
              403, "Forbidden", "You don't have permission to delete this group");
        }
        // Soft delete
        group.setStatus(Group.GroupStatus.DELETED);
        group.setUpdatedAt(Instant.now());
        group = groupDao.save(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Group deleted successfully");
        responseData.put("data", groupToMap(group));
        responseData.put("timestamp", Instant.now().toString());
        return createSuccessResponse(200, responseData, "application/json");
      }

      // Parse request body
      @SuppressWarnings("unchecked")
      Map<String, Object> requestMap = objectMapper.readValue(body, Map.class);

      // Check for delete action
      Object actionObj = requestMap.get("action");
      Object statusObj = requestMap.get("status");
      String actionStr = actionObj != null ? String.valueOf(actionObj).toLowerCase() : null;

      // Check if userIds are present (for remove users vs delete group)
      @SuppressWarnings("unchecked")
      List<String> userIdsForCheck = (List<String>) requestMap.get("userIds");
      boolean hasUserIds = userIdsForCheck != null && !userIdsForCheck.isEmpty();

      // Hard delete (permanent removal from DynamoDB)
      // Only treat "remove" as hard delete if NO userIds are provided
      // If userIds are provided, "remove" means remove users from group
      if ("harddelete".equals(actionStr)
          || "permanentdelete".equals(actionStr)
          || ("remove".equals(actionStr) && !hasUserIds)) {
        if (existingGroupOpt.isEmpty()) {
          return createErrorResponse(404, "Not Found", "Group not found: " + groupId);
        }
        Group group = existingGroupOpt.get();
        // Verify user is a member or creator
        if (!isGroupMemberOrCreator(group, userId)) {
          return createErrorResponse(
              403, "Forbidden", "You don't have permission to delete this group");
        }
        // Hard delete - permanently remove from DynamoDB
        groupDao.delete(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Group permanently deleted");
        responseData.put("data", groupToMap(group)); // Return group data before deletion
        responseData.put("timestamp", Instant.now().toString());
        return createSuccessResponse(200, responseData, "application/json");
      }

      // Soft delete (mark as DELETED but keep in database)
      if ("delete".equalsIgnoreCase(actionStr)
          || "DELETED".equals(String.valueOf(statusObj))
          || Group.GroupStatus.DELETED.getValue().equals(String.valueOf(statusObj))) {
        if (existingGroupOpt.isEmpty()) {
          return createErrorResponse(404, "Not Found", "Group not found: " + groupId);
        }
        Group group = existingGroupOpt.get();
        // Verify user is a member or creator
        if (!isGroupMemberOrCreator(group, userId)) {
          return createErrorResponse(
              403, "Forbidden", "You don't have permission to delete this group");
        }
        // Soft delete
        group.setStatus(Group.GroupStatus.DELETED);
        group.setUpdatedAt(Instant.now());
        group = groupDao.save(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Group deleted successfully (soft delete)");
        responseData.put("data", groupToMap(group));
        responseData.put("timestamp", Instant.now().toString());
        return createSuccessResponse(200, responseData, "application/json");
      }

      // Handle add/remove users or create/update group
      @SuppressWarnings("unchecked")
      List<String> userIds = (List<String>) requestMap.get("userIds");
      String action = actionObj != null ? String.valueOf(actionObj).toLowerCase() : null;

      if (existingGroupOpt.isPresent()) {
        // Group exists - handle add/remove users or update
        Group group = existingGroupOpt.get();

        // Log group details immediately after retrieval
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Retrieved group - groupId: "
                      + group.getGroupId()
                      + ", creator (getUserId): "
                      + group.getUserId()
                      + ", memberUserIds: "
                      + group.getMemberUserIds()
                      + ", status: "
                      + group.getStatus());
        }

        // Fix: If creatorUserId is missing (legacy group), try to infer it from memberUserIds
        if (group.getUserId() == null || group.getUserId().isBlank()) {
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: Group missing creatorUserId - attempting to fix. memberUserIds: "
                        + group.getMemberUserIds());
          }
          // If memberUserIds exists and is not empty, use the first member as creator
          if (group.getMemberUserIds() != null && !group.getMemberUserIds().isEmpty()) {
            String inferredCreator = group.getMemberUserIds().get(0).trim();
            group.setUserId(inferredCreator);
            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log("DEBUG: Inferred creator from memberUserIds: " + inferredCreator);
            }
            // Save the fix to DynamoDB
            group = groupDao.save(group);
            if (context != null && context.getLogger() != null) {
              context.getLogger().log("DEBUG: Updated group with creatorUserId");
            }
          }
        }

        // Check if group is deleted
        if (group.getStatus() == Group.GroupStatus.DELETED) {
          return createErrorResponse(410, "Gone", "Group has been deleted");
        }

        // Verify user is a member or creator
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Permission check - userId: "
                      + userId
                      + ", group creator: "
                      + group.getUserId()
                      + ", groupId: "
                      + group.getGroupId()
                      + ", memberUserIds: "
                      + group.getMemberUserIds());
        }

        // For "add" action, skip permission check if:
        // 1. Group has no members (allow first user to add)
        // 2. User is adding themselves to the group (special case - allow self-join)
        // 3. Group has no members AND no creator (legacy groups)
        boolean isAddAction = "add".equals(action) && userIds != null && !userIds.isEmpty();
        boolean hasNoMembers =
            group.getMemberUserIds() == null || group.getMemberUserIds().isEmpty();
        boolean hasNoCreator = group.getUserId() == null || group.getUserId().isBlank();
        
        // Check if user is adding themselves (case-insensitive comparison)
        boolean isAddingSelf = false;
        if (isAddAction && userId != null && !userId.isBlank()) {
          String trimmedUserId = userId.trim();
          for (String idToAdd : userIds) {
            if (idToAdd != null && idToAdd.trim().equalsIgnoreCase(trimmedUserId)) {
              isAddingSelf = true;
              break;
            }
          }
        }
        
        // Allow if: add action with no members, OR user is adding themselves, OR any update when
        // group has no members AND no creator
        boolean shouldSkipPermissionCheck =
            (isAddAction && hasNoMembers)
                || isAddingSelf
                || (hasNoMembers && hasNoCreator);

        if (!shouldSkipPermissionCheck && !isGroupMemberOrCreator(group, userId)) {
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: Permission check FAILED - userId: "
                        + userId
                        + ", group creator: "
                        + group.getUserId()
                        + ", groupId: "
                        + group.getGroupId()
                        + ", memberUserIds: "
                        + group.getMemberUserIds()
                        + ", userId.equals(creator): "
                        + (userId != null && group.getUserId() != null
                            ? userId.equals(group.getUserId())
                            : "null check")
                        + ", userId in members: "
                        + (group.getMemberUserIds() != null
                            ? group.getMemberUserIds().contains(userId)
                            : "null"));
          }
          return createErrorResponse(
              403, "Forbidden", "You don't have permission to modify this group");
        }

        if ("add".equals(action) && userIds != null && !userIds.isEmpty()) {
          // Permission check already passed above (including check for adding self)
          // Proceed with adding users to group

          // Add users to group
          List<String> currentMembers =
              group.getMemberUserIds() != null
                  ? new ArrayList<>(group.getMemberUserIds())
                  : new ArrayList<>();
          for (String newUserId : userIds) {
            if (newUserId != null && !currentMembers.contains(newUserId.trim())) {
              currentMembers.add(newUserId.trim());
            }
          }

          // If group had no creator and this is the first member, set them as creator
          if (group.getUserId() == null || group.getUserId().isBlank()) {
            if (!currentMembers.isEmpty()) {
              group.setUserId(currentMembers.get(0));
              if (context != null && context.getLogger() != null) {
                context
                    .getLogger()
                    .log("DEBUG: Setting first member as creator: " + currentMembers.get(0));
              }
            }
          }

          group.setMemberUserIds(currentMembers);
          group.setUpdatedAt(Instant.now());
          group = groupDao.save(group);

          Map<String, Object> responseData = new HashMap<>();
          responseData.put("success", true);
          responseData.put("message", "Users added to group successfully");
          responseData.put("data", groupToMap(group));
          responseData.put("timestamp", Instant.now().toString());
          return createSuccessResponse(200, responseData, "application/json");

        } else if ("remove".equals(action) && userIds != null && !userIds.isEmpty()) {
          // Remove users from group
          List<String> currentMembers =
              group.getMemberUserIds() != null
                  ? new ArrayList<>(group.getMemberUserIds())
                  : new ArrayList<>();

          if (context != null && context.getLogger() != null) {
            context.getLogger().log("Before removal - currentMembers: " + currentMembers);
            context.getLogger().log("User IDs to remove: " + userIds);
          }

          // Normalize user IDs for comparison (trim and handle case sensitivity)
          List<String> normalizedUserIdsToRemove = new ArrayList<>();
          for (String userIdToRemove : userIds) {
            if (userIdToRemove != null) {
              normalizedUserIdsToRemove.add(userIdToRemove.trim());
            }
          }

          // Remove all matching user IDs (case-sensitive exact match)
          List<String> updatedMembers = new ArrayList<>();
          for (String member : currentMembers) {
            String trimmedMember = member != null ? member.trim() : null;
            if (trimmedMember != null && !normalizedUserIdsToRemove.contains(trimmedMember)) {
              updatedMembers.add(member); // Keep original member (preserve any formatting)
            }
          }

          if (context != null && context.getLogger() != null) {
            context.getLogger().log("After removal - updatedMembers: " + updatedMembers);
            context
                .getLogger()
                .log("Removed count: " + (currentMembers.size() - updatedMembers.size()));
          }

          // Ensure at least one member remains (the creator)
          if (updatedMembers.isEmpty()) {
            return createErrorResponse(400, "Bad Request", "Cannot remove all members from group");
          }
          group.setMemberUserIds(updatedMembers);
          group.setUpdatedAt(Instant.now());
          group = groupDao.save(group);

          // Re-read group from DB to ensure we return the actual saved state
          Optional<Group> savedGroupOpt = groupDao.findByGroupId(group.getGroupId());
          if (savedGroupOpt.isPresent()) {
            group = savedGroupOpt.get();
            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log("After save - group memberUserIds: " + group.getMemberUserIds());
            }
          }

          Map<String, Object> responseData = new HashMap<>();
          responseData.put("success", true);
          responseData.put("message", "Users removed from group successfully");
          responseData.put("data", groupToMap(group));
          responseData.put("timestamp", Instant.now().toString());
          return createSuccessResponse(200, responseData, "application/json");

        } else if ("addexperience".equals(action) || "addExperience".equals(action)) {
          // Add group to experience(s)
          @SuppressWarnings("unchecked")
          List<String> experienceIds = (List<String>) requestMap.get("experienceIds");
          if (experienceIds == null || experienceIds.isEmpty()) {
            return createErrorResponse(
                400, "Bad Request", "experienceIds list is required for adding experiences");
          }

          // Verify all experiences exist
          List<String> addedExperiences = new ArrayList<>();
          List<String> invalidExperiences = new ArrayList<>();
          for (String experienceId : experienceIds) {
            if (experienceId == null || experienceId.trim().isEmpty()) {
              continue;
            }
            String normalizedExperienceId =
                experienceId.startsWith("EXPERIENCE#")
                    ? experienceId
                    : "EXPERIENCE#" + experienceId;
            Optional<Experience> experienceOpt =
                experienceDao.findByExperienceId(normalizedExperienceId.replace("EXPERIENCE#", ""));
            if (experienceOpt.isEmpty()) {
              invalidExperiences.add(experienceId);
            } else {
              // Check if relationship already exists
              List<GroupExperience> existing = groupExperienceDao.findByGroupId(group.getGroupId());
              boolean alreadyExists =
                  existing.stream()
                      .anyMatch(
                          ge -> ge.getExperienceId().equals(experienceOpt.get().getExperienceId()));
              if (!alreadyExists) {
                // Create group-experience relationship
                GroupExperience groupExperience =
                    new GroupExperience(group.getGroupId(), experienceOpt.get().getExperienceId());
                groupExperienceDao.save(groupExperience);
                addedExperiences.add(experienceOpt.get().getExperienceId());
              }
            }
          }

          if (!invalidExperiences.isEmpty()) {
            return createErrorResponse(
                404,
                "Not Found",
                "Some experiences not found: " + String.join(", ", invalidExperiences));
          }

          Map<String, Object> responseData = new HashMap<>();
          responseData.put("success", true);
          responseData.put("message", "Group added to experience(s) successfully");
          responseData.put("data", groupToMap(group));
          responseData.put("addedExperiences", addedExperiences);
          responseData.put("timestamp", Instant.now().toString());
          return createSuccessResponse(200, responseData, "application/json");

        } else if ("removeexperience".equals(action) || "removeExperience".equals(action)) {
          // Remove group from experience(s)
          @SuppressWarnings("unchecked")
          List<String> experienceIds = (List<String>) requestMap.get("experienceIds");
          if (experienceIds == null || experienceIds.isEmpty()) {
            return createErrorResponse(
                400, "Bad Request", "experienceIds list is required for removing experiences");
          }

          // Find and remove group-experience relationships
          List<GroupExperience> existingRelationships =
              groupExperienceDao.findByGroupId(group.getGroupId());
          List<String> removedExperiences = new ArrayList<>();
          List<String> notFoundExperiences = new ArrayList<>();

          for (String experienceId : experienceIds) {
            if (experienceId == null || experienceId.trim().isEmpty()) {
              continue;
            }
            String normalizedExperienceId =
                experienceId.startsWith("EXPERIENCE#")
                    ? experienceId
                    : "EXPERIENCE#" + experienceId;

            // Find the relationship to remove
            Optional<GroupExperience> relationshipToRemove =
                existingRelationships.stream()
                    .filter(
                        ge -> {
                          String geExpId = ge.getExperienceId();
                          String targetExpId = normalizedExperienceId.replace("EXPERIENCE#", "");
                          return geExpId.equals(targetExpId)
                              || geExpId.equals(normalizedExperienceId);
                        })
                    .findFirst();

            if (relationshipToRemove.isPresent()) {
              // Note: GroupExperienceDao doesn't have a delete method yet
              // For now, we'll need to add it or use a workaround
              // Since DynamoDB uses composite key (groupId, experienceId), we can delete it
              removedExperiences.add(relationshipToRemove.get().getExperienceId());
            } else {
              notFoundExperiences.add(experienceId);
            }
          }

          // Delete the relationships
          for (String experienceId : experienceIds) {
            if (experienceId == null || experienceId.trim().isEmpty()) {
              continue;
            }
            String normalizedExperienceId =
                experienceId.startsWith("EXPERIENCE#")
                    ? experienceId
                    : "EXPERIENCE#" + experienceId;

            // Find the relationship to remove
            Optional<GroupExperience> relationshipToRemove =
                existingRelationships.stream()
                    .filter(
                        ge -> {
                          String geExpId = ge.getExperienceId();
                          String targetExpId = normalizedExperienceId.replace("EXPERIENCE#", "");
                          return geExpId.equals(targetExpId)
                              || geExpId.equals(normalizedExperienceId);
                        })
                    .findFirst();

            if (relationshipToRemove.isPresent()) {
              groupExperienceDao.delete(
                  group.getGroupId(), relationshipToRemove.get().getExperienceId());
              removedExperiences.add(relationshipToRemove.get().getExperienceId());
            } else {
              notFoundExperiences.add(experienceId);
            }
          }

          Map<String, Object> responseData = new HashMap<>();
          responseData.put("success", true);
          responseData.put("message", "Group removed from experience(s) successfully");
          responseData.put("data", groupToMap(group));
          responseData.put("removedExperiences", removedExperiences);
          if (!notFoundExperiences.isEmpty()) {
            responseData.put("notFoundExperiences", notFoundExperiences);
          }
          responseData.put("timestamp", Instant.now().toString());
          return createSuccessResponse(200, responseData, "application/json");

        } else {
          // Update group fields (groupName, description, etc.)
          if (requestMap.containsKey("groupName")) {
            group.setGroupName((String) requestMap.get("groupName"));
          }
          if (requestMap.containsKey("description")) {
            group.setDescription((String) requestMap.get("description"));
          }
          if (requestMap.containsKey("status")) {
            group.setStatus(Group.GroupStatus.fromValue(String.valueOf(requestMap.get("status"))));
          }
          // Handle userIds in request body (even without action field)
          if (requestMap.containsKey("userIds")) {
            @SuppressWarnings("unchecked")
            List<String> userIdsFromRequest = (List<String>) requestMap.get("userIds");
            if (userIdsFromRequest != null && !userIdsFromRequest.isEmpty()) {
              // Set memberUserIds from request
              List<String> normalizedUserIds = new ArrayList<>();
              for (String uid : userIdsFromRequest) {
                if (uid != null && !uid.trim().isEmpty()) {
                  normalizedUserIds.add(uid.trim());
                }
              }
              group.setMemberUserIds(normalizedUserIds);

              // If group has no creator, set first userId as creator
              if (group.getUserId() == null || group.getUserId().isBlank()) {
                if (!normalizedUserIds.isEmpty()) {
                  group.setUserId(normalizedUserIds.get(0));
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log("DEBUG: Setting first userId as creator: " + normalizedUserIds.get(0));
                  }
                }
              }

              if (context != null && context.getLogger() != null) {
                context
                    .getLogger()
                    .log("DEBUG: Updated memberUserIds from request: " + normalizedUserIds);
              }
            }
          }

          // Handle experienceIds in request body (even without action field)
          // This allows adding both userIds and experienceIds in a single request
          List<String> addedExperiences = new ArrayList<>();
          List<String> invalidExperiences = new ArrayList<>();
          if (requestMap.containsKey("experienceIds")) {
            @SuppressWarnings("unchecked")
            List<String> experienceIds = (List<String>) requestMap.get("experienceIds");
            if (experienceIds != null && !experienceIds.isEmpty()) {
              // Verify all experiences exist and add them
              for (String experienceId : experienceIds) {
                if (experienceId == null || experienceId.trim().isEmpty()) {
                  continue;
                }
                String normalizedExperienceId =
                    experienceId.startsWith("EXPERIENCE#")
                        ? experienceId
                        : "EXPERIENCE#" + experienceId;
                Optional<Experience> experienceOpt =
                    experienceDao.findByExperienceId(
                        normalizedExperienceId.replace("EXPERIENCE#", ""));
                if (experienceOpt.isEmpty()) {
                  invalidExperiences.add(experienceId);
                } else {
                  // Check if relationship already exists
                  List<GroupExperience> existing =
                      groupExperienceDao.findByGroupId(group.getGroupId());
                  boolean alreadyExists =
                      existing.stream()
                          .anyMatch(
                              ge ->
                                  ge.getExperienceId()
                                      .equals(experienceOpt.get().getExperienceId()));
                  if (!alreadyExists) {
                    // Create group-experience relationship
                    GroupExperience groupExperience =
                        new GroupExperience(
                            group.getGroupId(), experienceOpt.get().getExperienceId());
                    groupExperienceDao.save(groupExperience);
                    addedExperiences.add(experienceOpt.get().getExperienceId());
                  }
                }
              }

              if (context != null && context.getLogger() != null) {
                context
                    .getLogger()
                    .log(
                        "DEBUG: Added "
                            + addedExperiences.size()
                            + " experiences, invalid: "
                            + invalidExperiences.size());
              }
            }
          }

          group.setUpdatedAt(Instant.now());
          group = groupDao.save(group);

          Map<String, Object> responseData = new HashMap<>();
          responseData.put("success", true);

          // Build appropriate message based on what was updated
          List<String> messages = new ArrayList<>();
          if (requestMap.containsKey("userIds")
              || requestMap.containsKey("groupName")
              || requestMap.containsKey("description")
              || requestMap.containsKey("status")) {
            messages.add("Group updated successfully");
          }
          if (!addedExperiences.isEmpty()) {
            messages.add("Experience(s) added successfully");
          }
          if (!invalidExperiences.isEmpty()) {
            messages.add("Some experiences not found: " + String.join(", ", invalidExperiences));
          }

          responseData.put("message", String.join(". ", messages));
          responseData.put("data", groupToMap(group));
          if (!addedExperiences.isEmpty()) {
            responseData.put("addedExperiences", addedExperiences);
          }
          if (!invalidExperiences.isEmpty()) {
            responseData.put("invalidExperiences", invalidExperiences);
          }
          responseData.put("timestamp", Instant.now().toString());
          return createSuccessResponse(200, responseData, "application/json");
        }
      } else {
        // Group doesn't exist - create new group
        if (userIds == null || userIds.isEmpty()) {
          return createErrorResponse(
              400, "Bad Request", "userIds list is required for creating a new group");
        }

        // Create group with first userId as the creator
        Group group = new Group(groupId, userIds.get(0));
        group.setGroupName((String) requestMap.get("groupName"));
        group.setDescription((String) requestMap.get("description"));
        group.setMemberUserIds(userIds);
        if (requestMap.containsKey("status")) {
          group.setStatus(Group.GroupStatus.fromValue(String.valueOf(requestMap.get("status"))));
        }

        // Save group
        group = groupDao.save(group);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Group created successfully");
        responseData.put("data", groupToMap(group));
        responseData.put("timestamp", Instant.now().toString());
        return createSuccessResponse(201, responseData, "application/json");
      }
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Check if user is a member or creator of the group.
   *
   * @param group Group to check
   * @param userId User ID to check
   * @return true if user is a member or creator, false otherwise
   */
  private boolean isGroupMemberOrCreator(Group group, String userId) {
    if (userId == null || userId.isBlank()) {
      return false;
    }
    // Check if user is the creator (userId field contains creatorUserId)
    String creatorUserId = group.getUserId();
    if (userId.equals(creatorUserId)) {
      return true;
    }
    // Check if user is in memberUserIds list
    if (group.getMemberUserIds() != null) {
      // Trim and compare to handle any whitespace issues
      for (String memberId : group.getMemberUserIds()) {
        if (memberId != null && memberId.trim().equals(userId.trim())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Convert Group to Map for response.
   *
   * @param group Group to convert
   * @return Map representation of group
   */
  private Map<String, Object> groupToMap(Group group) {
    Map<String, Object> groupData = new HashMap<>();
    groupData.put("groupId", group.getGroupId());
    groupData.put("groupName", group.getGroupName());
    groupData.put("description", group.getDescription());
    groupData.put("status", group.getStatus() != null ? group.getStatus().getValue() : null);
    groupData.put("memberUserIds", group.getMemberUserIds());
    groupData.put("createdAt", group.getCreatedAt());
    groupData.put("updatedAt", group.getUpdatedAt());
    return groupData;
  }

  /**
   * Handle GET /experiences/{experienceId}/interested-users - Get all users who are interested in
   * an experience.
   */
  private APIGatewayProxyResponseEvent handleGetInterestedUsers(
      String experienceId, Context context) {
    try {
      // Verify experience exists
      Optional<Experience> experienceOpt = experienceDao.findByExperienceId(experienceId);
      if (experienceOpt.isEmpty()) {
        return createErrorResponse(404, "Not Found", "Experience not found: " + experienceId);
      }

      // Get all users interested in this experience
      List<UserExperience> interestedUserExperiences =
          userExperienceDao.findInterestedUsersByExperienceId(experienceId);

      if (context != null && context.getLogger() != null) {
        context
            .getLogger()
            .log(
                "DEBUG: Found "
                    + interestedUserExperiences.size()
                    + " users interested in experience "
                    + experienceId);
      }

      // Fetch user profiles for each interested user
      List<Map<String, Object>> interestedUsers = new ArrayList<>();
      for (UserExperience ue : interestedUserExperiences) {
        String userId = ue.getUserId();
        if (userId != null && !userId.isBlank()) {
          Optional<UserProfile> userProfileOpt = findByUserId(userId, context);
          if (userProfileOpt.isPresent()) {
            UserProfile profile = userProfileOpt.get();
            Map<String, Object> userData = new HashMap<>();

            // Add user profile data
            userData.put("userId", profile.getUserId());
            if (profile.getDateOfBirth() != null) {
              userData.put("dateOfBirth", profile.getDateOfBirth().toString());
            }
            if (profile.getAddress() != null) {
              userData.put("address", profile.getAddress());
            }
            if (profile.getCity() != null) {
              userData.put("city", profile.getCity());
            }
            if (profile.getState() != null) {
              userData.put("state", profile.getState());
            }
            if (profile.getZipCode() != null) {
              userData.put("zipCode", profile.getZipCode());
            }
            if (profile.getCountry() != null) {
              userData.put("country", profile.getCountry());
            }
            if (profile.getLatitude() != null) {
              userData.put("latitude", profile.getLatitude());
            }
            if (profile.getLongitude() != null) {
              userData.put("longitude", profile.getLongitude());
            }
            if (profile.getGender() != null) {
              userData.put("gender", profile.getGender());
            }
            if (profile.getProfession() != null) {
              userData.put("profession", profile.getProfession());
            }
            if (profile.getCompany() != null) {
              userData.put("company", profile.getCompany());
            }
            if (profile.getBio() != null) {
              userData.put("bio", profile.getBio());
            }
            if (profile.getPhoneNumber() != null) {
              userData.put("phoneNumber", profile.getPhoneNumber());
            }
            if (profile.getStatus() != null) {
              userData.put("status", profile.getStatus().getValue());
            }

            // Add interest metadata
            userData.put("exp-interest", ue.getExpInterest());
            if (ue.getInterestScore() != null) {
              userData.put("interestScore", ue.getInterestScore());
            }
            if (ue.getCreatedAt() != null) {
              userData.put("interestedAt", ue.getCreatedAt().toString());
            }

            interestedUsers.add(userData);
          } else {
            // User profile not found, but still include basic info
            if (context != null && context.getLogger() != null) {
              context.getLogger().log("Warning: User profile not found for userId: " + userId);
            }
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", userId);
            userData.put("exp-interest", ue.getExpInterest());
            if (ue.getInterestScore() != null) {
              userData.put("interestScore", ue.getInterestScore());
            }
            if (ue.getCreatedAt() != null) {
              userData.put("interestedAt", ue.getCreatedAt().toString());
            }
            interestedUsers.add(userData);
          }
        }
      }

      // Build response
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Interested users retrieved successfully");
      responseData.put("data", interestedUsers);
      responseData.put("count", interestedUsers.size());
      responseData.put("timestamp", Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error retrieving interested users: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
        e.printStackTrace();
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle GET /experiences/{experienceId}/attended-users - Get all users who attended (paid) for
   * an experience.
   */
  private APIGatewayProxyResponseEvent handleGetAttendedUsers(
      String experienceId, Context context) {
    try {
      // Verify experience exists
      Optional<Experience> experienceOpt = experienceDao.findByExperienceId(experienceId);
      if (experienceOpt.isEmpty()) {
        return createErrorResponse(404, "Not Found", "Experience not found: " + experienceId);
      }

      // Get all users for this experience
      List<UserExperience> allUserExperiences = userExperienceDao.findByExperienceId(experienceId);

      // Filter to only include users who attended (status = PAID or ATTENDED, or paid = true)
      List<UserExperience> attendedUserExperiences =
          allUserExperiences.stream()
              .filter(
                  ue -> {
                    // Check if status is PAID or ATTENDED
                    if (ue.getStatus() == UserExperience.UserExperienceStatus.PAID
                        || ue.getStatus() == UserExperience.UserExperienceStatus.ATTENDED) {
                      return true;
                    }
                    // Check if paid field is true
                    if (ue.getPaid() != null && ue.getPaid()) {
                      return true;
                    }
                    // Check if paymentDetails exists (backward compatibility)
                    if (ue.getPaymentDetails() != null) {
                      return true;
                    }
                    return false;
                  })
              .collect(Collectors.toList());

      if (context != null && context.getLogger() != null) {
        context
            .getLogger()
            .log(
                "DEBUG: Found "
                    + attendedUserExperiences.size()
                    + " users who attended experience "
                    + experienceId
                    + " (out of "
                    + allUserExperiences.size()
                    + " total users)");
      }

      // Fetch user profiles for each attended user
      List<Map<String, Object>> attendedUsers = new ArrayList<>();
      for (UserExperience ue : attendedUserExperiences) {
        String userId = ue.getUserId();
        if (userId != null && !userId.isBlank()) {
          Optional<UserProfile> userProfileOpt = findByUserId(userId, context);
          if (userProfileOpt.isPresent()) {
            UserProfile profile = userProfileOpt.get();
            Map<String, Object> userData = new HashMap<>();

            // Add user profile data
            userData.put("userId", profile.getUserId());
            if (profile.getDateOfBirth() != null) {
              userData.put("dateOfBirth", profile.getDateOfBirth().toString());
            }
            if (profile.getAddress() != null) {
              userData.put("address", profile.getAddress());
            }
            if (profile.getCity() != null) {
              userData.put("city", profile.getCity());
            }
            if (profile.getState() != null) {
              userData.put("state", profile.getState());
            }
            if (profile.getZipCode() != null) {
              userData.put("zipCode", profile.getZipCode());
            }
            if (profile.getCountry() != null) {
              userData.put("country", profile.getCountry());
            }
            if (profile.getLatitude() != null) {
              userData.put("latitude", profile.getLatitude());
            }
            if (profile.getLongitude() != null) {
              userData.put("longitude", profile.getLongitude());
            }
            if (profile.getGender() != null) {
              userData.put("gender", profile.getGender());
            }
            if (profile.getProfession() != null) {
              userData.put("profession", profile.getProfession());
            }
            if (profile.getCompany() != null) {
              userData.put("company", profile.getCompany());
            }
            if (profile.getBio() != null) {
              userData.put("bio", profile.getBio());
            }
            if (profile.getPhoneNumber() != null) {
              userData.put("phoneNumber", profile.getPhoneNumber());
            }
            if (profile.getStatus() != null) {
              userData.put("status", profile.getStatus().getValue());
            }

            // Add payment/attendance metadata
            if (ue.getStatus() != null) {
              userData.put("userStatus", ue.getStatus().getValue());
            }
            if (ue.getPaid() != null) {
              userData.put("paid", ue.getPaid());
            }
            if (ue.getPaymentDetails() != null) {
              Map<String, Object> paymentDetails = new HashMap<>();
              if (ue.getPaymentDetails().getAmount() != null) {
                paymentDetails.put("amount", ue.getPaymentDetails().getAmount());
              }
              if (ue.getPaymentDetails().getCurrency() != null) {
                paymentDetails.put("currency", ue.getPaymentDetails().getCurrency());
              }
              if (ue.getPaymentDetails().getPaymentMethod() != null) {
                paymentDetails.put("paymentMethod", ue.getPaymentDetails().getPaymentMethod());
              }
              if (ue.getPaymentDetails().getPaymentDate() != null) {
                paymentDetails.put(
                    "paymentDate", ue.getPaymentDetails().getPaymentDate().toString());
              }
              if (ue.getPaymentDetails().getTransactionId() != null) {
                paymentDetails.put("transactionId", ue.getPaymentDetails().getTransactionId());
              }
              if (!paymentDetails.isEmpty()) {
                userData.put("paymentDetails", paymentDetails);
              }
            }
            if (ue.getCreatedAt() != null) {
              userData.put("paidAt", ue.getCreatedAt().toString());
            }
            if (ue.getUpdatedAt() != null) {
              userData.put("updatedAt", ue.getUpdatedAt().toString());
            }

            attendedUsers.add(userData);
          } else {
            // User profile not found, but still include basic info
            if (context != null && context.getLogger() != null) {
              context.getLogger().log("Warning: User profile not found for userId: " + userId);
            }
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", userId);
            if (ue.getStatus() != null) {
              userData.put("userStatus", ue.getStatus().getValue());
            }
            if (ue.getPaid() != null) {
              userData.put("paid", ue.getPaid());
            }
            if (ue.getPaymentDetails() != null) {
              Map<String, Object> paymentDetails = new HashMap<>();
              if (ue.getPaymentDetails().getAmount() != null) {
                paymentDetails.put("amount", ue.getPaymentDetails().getAmount());
              }
              if (ue.getPaymentDetails().getCurrency() != null) {
                paymentDetails.put("currency", ue.getPaymentDetails().getCurrency());
              }
              if (ue.getPaymentDetails().getPaymentMethod() != null) {
                paymentDetails.put("paymentMethod", ue.getPaymentDetails().getPaymentMethod());
              }
              if (ue.getPaymentDetails().getPaymentDate() != null) {
                paymentDetails.put(
                    "paymentDate", ue.getPaymentDetails().getPaymentDate().toString());
              }
              if (ue.getPaymentDetails().getTransactionId() != null) {
                paymentDetails.put("transactionId", ue.getPaymentDetails().getTransactionId());
              }
              if (!paymentDetails.isEmpty()) {
                userData.put("paymentDetails", paymentDetails);
              }
            }
            if (ue.getCreatedAt() != null) {
              userData.put("paidAt", ue.getCreatedAt().toString());
            }
            attendedUsers.add(userData);
          }
        }
      }

      // Build response
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Attended users retrieved successfully");
      responseData.put("data", attendedUsers);
      responseData.put("count", attendedUsers.size());
      responseData.put("timestamp", Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error retrieving attended users: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
        e.printStackTrace();
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle GET /experiences - Get all users who are interested in upcoming experiences (where
   * current_time <= experience_time).
   */
  private APIGatewayProxyResponseEvent handleGetAllInterestedUsers(Context context) {
    try {
      if (context != null && context.getLogger() != null) {
        context
            .getLogger()
            .log(
                "DEBUG: handleGetAllInterestedUsers called - getting all users interested in upcoming experiences");
      }

      // Get all UserExperience records where exp-interest = true
      List<UserExperience> allInterestedUserExperiences =
          userExperienceDao.findAllInterestedUsers();

      if (context != null && context.getLogger() != null) {
        context
            .getLogger()
            .log(
                "DEBUG: Found "
                    + allInterestedUserExperiences.size()
                    + " UserExperience records with exp-interest = true");
      }

      // Get current time
      java.time.LocalDateTime now = java.time.LocalDateTime.now();
      java.time.Instant nowInstant = now.atZone(java.time.ZoneId.systemDefault()).toInstant();

      // Filter by experience date/time >= current time and fetch user/experience details
      List<Map<String, Object>> result = new ArrayList<>();
      for (UserExperience ue : allInterestedUserExperiences) {
        String experienceId = ue.getExperienceId();
        String userId = ue.getUserId();

        if (experienceId == null || experienceId.isBlank() || userId == null || userId.isBlank()) {
          continue;
        }

        // Get experience details
        Optional<Experience> experienceOpt = experienceDao.findByExperienceId(experienceId);
        if (experienceOpt.isEmpty()) {
          continue; // Experience not found, skip
        }

        Experience experience = experienceOpt.get();

        // Check if experience date/time >= current time
        if (experience.getExperienceDate() != null && experience.getStartTime() != null) {
          java.time.LocalDateTime experienceStartDateTime =
              experience.getExperienceDate().atTime(experience.getStartTime());
          java.time.Instant experienceStartInstant =
              experienceStartDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();

          // Filter: only include if experience start time >= current time
          if (experienceStartInstant.isBefore(nowInstant)) {
            continue; // Experience has already started or passed, skip
          }
        } else {
          // No date/time, skip
          continue;
        }

        // Get user profile
        Optional<UserProfile> userProfileOpt = findByUserId(userId, context);
        Map<String, Object> userData = new HashMap<>();

        if (userProfileOpt.isPresent()) {
          UserProfile profile = userProfileOpt.get();
          userData.put("userId", profile.getUserId());
          if (profile.getDateOfBirth() != null) {
            userData.put("dateOfBirth", profile.getDateOfBirth().toString());
          }
          if (profile.getAddress() != null) {
            userData.put("address", profile.getAddress());
          }
          if (profile.getCity() != null) {
            userData.put("city", profile.getCity());
          }
          if (profile.getState() != null) {
            userData.put("state", profile.getState());
          }
          if (profile.getZipCode() != null) {
            userData.put("zipCode", profile.getZipCode());
          }
          if (profile.getCountry() != null) {
            userData.put("country", profile.getCountry());
          }
          if (profile.getLatitude() != null) {
            userData.put("latitude", profile.getLatitude());
          }
          if (profile.getLongitude() != null) {
            userData.put("longitude", profile.getLongitude());
          }
          if (profile.getGender() != null) {
            userData.put("gender", profile.getGender());
          }
          if (profile.getProfession() != null) {
            userData.put("profession", profile.getProfession());
          }
          if (profile.getCompany() != null) {
            userData.put("company", profile.getCompany());
          }
          if (profile.getBio() != null) {
            userData.put("bio", profile.getBio());
          }
          if (profile.getPhoneNumber() != null) {
            userData.put("phoneNumber", profile.getPhoneNumber());
          }
          if (profile.getStatus() != null) {
            userData.put("status", profile.getStatus().getValue());
          }
        } else {
          // User profile not found, include basic info
          userData.put("userId", userId);
        }

        // Add experience details
        Map<String, Object> experienceData = experienceToMap(experience);
        userData.put("experience", experienceData);

        // Add interest metadata
        userData.put("exp-interest", ue.getExpInterest());
        if (ue.getInterestScore() != null) {
          userData.put("interestScore", ue.getInterestScore());
        }
        if (ue.getCreatedAt() != null) {
          userData.put("interestedAt", ue.getCreatedAt().toString());
        }

        result.add(userData);
      }

      // Build response
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put(
          "message", "All users interested in upcoming experiences retrieved successfully");
      responseData.put("data", result);
      responseData.put("count", result.size());
      responseData.put("timestamp", Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error retrieving all interested users: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
        e.printStackTrace();
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /** Handle GET /experiences/{experienceId} - Get experience details with optional groupId. */
  private APIGatewayProxyResponseEvent handleGetExperience(String experienceId, Context context) {
    try {
      Optional<Experience> experienceOpt = experienceDao.findByExperienceId(experienceId);

      if (experienceOpt.isEmpty()) {
        return createErrorResponse(404, "Not Found", "Experience not found: " + experienceId);
      }

      Experience experience = experienceOpt.get();

      // Get related groups for this experience
      List<GroupExperience> groupExperiences = groupExperienceDao.findByExperienceId(experienceId);
      List<String> groupIds =
          groupExperiences.stream().map(GroupExperience::getGroupId).collect(Collectors.toList());

      // Build response
      Map<String, Object> experienceData = new HashMap<>();
      experienceData.put("experienceId", experience.getExperienceId());
      experienceData.put("title", experience.getTitle());
      experienceData.put("description", experience.getDescription());
      experienceData.put("type", experience.getType());
      experienceData.put("status", experience.getStatus());
      experienceData.put("location", experience.getLocation());
      experienceData.put("address", experience.getAddress());
      experienceData.put("city", experience.getCity());
      experienceData.put("country", experience.getCountry());
      experienceData.put("latitude", experience.getLatitude());
      experienceData.put("longitude", experience.getLongitude());
      experienceData.put("experienceDate", experience.getExperienceDate());
      experienceData.put("startTime", experience.getStartTime());
      experienceData.put("endTime", experience.getEndTime());
      experienceData.put("pricePerPerson", experience.getPricePerPerson());
      experienceData.put("currency", experience.getCurrency());
      experienceData.put("maxCapacity", experience.getMaxCapacity());
      experienceData.put("currentBookings", experience.getCurrentBookings());
      experienceData.put("groupId", groupIds.isEmpty() ? null : groupIds.get(0)); // Optional
      experienceData.put("groupIds", groupIds);
      experienceData.put("createdAt", experience.getCreatedAt());
      experienceData.put("updatedAt", experience.getUpdatedAt());

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Experience retrieved successfully");
      responseData.put("data", experienceData);
      responseData.put("timestamp", Instant.now().toString());
      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle GET
   * /experiences?userId={userId}&groupId={groupId}&lat={lat}&lon={lon}&radius={radius}&interested={true}&past={true}&upcoming={true}
   * - Get experiences with optional filters.
   *
   * <p>Query Parameters:
   *
   * <ul>
   *   <li>lat, lon: Get nearby experiences (requires both)
   *   <li>radius: Filter nearby experiences by radius in kilometers (optional, default: no limit)
   *   <li>userId: Get user's experiences
   *   <li>interested: If true, get experiences user has shown interest in (requires userId)
   *   <li>past: If true, get past experiences user attended (requires userId)
   *   <li>upcoming: If true, get upcoming experiences user has paid for (requires userId)
   *   <li>groupId: Get experiences for a group
   * </ul>
   */
  private APIGatewayProxyResponseEvent handleGetExperiences(
      String userId,
      String groupId,
      String lat,
      String lon,
      String radius,
      String interested,
      String past,
      String upcoming,
      Context context) {
    try {
      if (context != null && context.getLogger() != null) {
        context
            .getLogger()
            .log(
                "DEBUG: handleGetExperiences called - lat=["
                    + lat
                    + "], lon=["
                    + lon
                    + "], radius=["
                    + radius
                    + "], userId=["
                    + userId
                    + "]");
        context
            .getLogger()
            .log("DEBUG: lat != null: " + (lat != null) + ", lon != null: " + (lon != null));
        if (lat != null) {
          context
              .getLogger()
              .log("DEBUG: lat.isBlank(): " + lat.isBlank() + ", lat.length(): " + lat.length());
        }
        if (lon != null) {
          context
              .getLogger()
              .log("DEBUG: lon.isBlank(): " + lon.isBlank() + ", lon.length(): " + lon.length());
        }
      }

      List<Map<String, Object>> experiences = new ArrayList<>();

      // IMPORTANT: If past=true or upcoming=true or interested=true, we MUST query UserExperience
      // records regardless of location. Location filtering should NOT apply to user-specific
      // queries.
      boolean isUserSpecificQuery =
          (past != null && ("true".equalsIgnoreCase(past) || "1".equals(past)))
              || (upcoming != null && ("true".equalsIgnoreCase(upcoming) || "1".equals(upcoming)))
              || (interested != null
                  && ("true".equalsIgnoreCase(interested) || "1".equals(interested)));

      // If user-specific query (past/upcoming/interested), skip location-based filtering
      // and go directly to UserExperience query path
      if (isUserSpecificQuery && userId != null && !userId.isBlank()) {
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: User-specific query detected (past="
                      + past
                      + ", upcoming="
                      + upcoming
                      + ", interested="
                      + interested
                      + ") - skipping location filtering");
        }
        // Force lat/lon to null to ensure we use UserExperience query path
        lat = null;
        lon = null;
      }

      // If lat/lon provided AND not a user-specific query, get nearby experiences using geohashing
      if (lat != null && lon != null && !lat.isBlank() && !lon.isBlank() && !isUserSpecificQuery) {
        if (context != null && context.getLogger() != null) {
          context.getLogger().log("DEBUG: Entering nearby experiences path");
        }
        if (context != null && context.getLogger() != null) {
          context.getLogger().log("DEBUG: lat=" + lat + ", lon=" + lon + ", radius=" + radius);
        }
        try {
          double latitude = Double.parseDouble(lat);
          double longitude = Double.parseDouble(lon);
          Double radiusKm = null;
          if (radius != null && !radius.isBlank()) {
            try {
              radiusKm = Double.parseDouble(radius);
              if (radiusKm < 0) {
                return createErrorResponse(400, "Bad Request", "Radius must be non-negative");
              }
            } catch (NumberFormatException e) {
              return createErrorResponse(400, "Bad Request", "Invalid radius format");
            }
          }
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log(
                    "DEBUG: Calling getNearbyExperiences with lat="
                        + latitude
                        + ", lon="
                        + longitude
                        + ", radiusKm="
                        + radiusKm);
          }
          experiences = getNearbyExperiences(latitude, longitude, radiusKm, context);
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log("DEBUG: getNearbyExperiences returned " + experiences.size() + " experiences");
          }
        } catch (NumberFormatException e) {
          if (context != null && context.getLogger() != null) {
            context
                .getLogger()
                .log("DEBUG: NumberFormatException parsing lat/lon: " + e.getMessage());
          }
          return createErrorResponse(400, "Bad Request", "Invalid lat/lon format");
        }
      } else if (userId != null && !userId.isBlank()) {
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: NOT entering nearby experiences path - lat="
                      + lat
                      + ", lon="
                      + lon
                      + ", userId="
                      + userId);
          context.getLogger().log("DEBUG: Querying UserExperience for userId=[" + userId + "]");
          context
              .getLogger()
              .log("DEBUG: interested=" + interested + ", past=" + past + ", upcoming=" + upcoming);
        }
        // Query UserExperience table for userId
        List<UserExperience> userExperiences = userExperienceDao.findByUserId(userId);
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Found "
                      + userExperiences.size()
                      + " UserExperience records for userId=["
                      + userId
                      + "]");
          for (UserExperience ue : userExperiences) {
            context
                .getLogger()
                .log(
                    "DEBUG: UserExperience - experienceId=["
                        + ue.getExperienceId()
                        + "], status=["
                        + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                        + "], paid=["
                        + (ue.getPaid() != null ? ue.getPaid() : "null")
                        + "], expInterest=["
                        + ue.getExpInterest()
                        + "], hasPaymentDetails=["
                        + (ue.getPaymentDetails() != null)
                        + "]");
          }
        }
        List<Map<String, Object>> filteredExperiences = new ArrayList<>();

        for (UserExperience ue : userExperiences) {
          String experienceId = ue.getExperienceId();
          if (experienceId == null || experienceId.isBlank()) {
            // ExperienceId is missing - this is a data issue
            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "WARNING: UserExperience record found with null/blank experienceId for userId=["
                          + userId
                          + "], status=["
                          + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                          + "], paid=["
                          + (ue.getPaid() != null ? ue.getPaid() : "null")
                          + "]. This record cannot be linked to an Experience. "
                          + "To fix: Create a proper UserExperience record by calling PUT /users/{userId}/experiences/{experienceId}/payment");
            }
            continue; // Skip records without experienceId
          }

          // ExperienceId is present, proceed with filtering
          Optional<Experience> expOpt = experienceDao.findByExperienceId(experienceId);
          if (expOpt.isPresent()) {
            Experience experience = expOpt.get();

            // Filter by interested status (using exp-interest field)
            // Also filter by upcoming experiences only (current_time <= experience_time)
            boolean interestedFilter =
                interested != null
                    && ("true".equalsIgnoreCase(interested) || "1".equals(interested));
            if (interestedFilter) {
              // Check exp-interest field (Boolean)
              // Fallback: if expInterest is null, check old status field for backward compatibility
              Boolean expInterest = ue.getExpInterest();
              boolean isInterested = false;

              if (expInterest != null && expInterest) {
                isInterested = true;
              } else if (expInterest == null
                  && ue.getStatus() == UserExperience.UserExperienceStatus.INTERESTED) {
                // Backward compatibility: treat old status=INTERESTED as interested=true
                isInterested = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: Using status=INTERESTED as fallback for expInterest (backward compatibility)");
                }
              }

              if (context != null && context.getLogger() != null) {
                context
                    .getLogger()
                    .log(
                        "DEBUG: Filtering by interested=true - experienceId=["
                            + experienceId
                            + "], expInterest=["
                            + expInterest
                            + "], status=["
                            + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                            + "], isInterested=["
                            + isInterested
                            + "]");
              }

              if (!isInterested) {
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: Skipping experience ["
                              + experienceId
                              + "] - not interested (expInterest="
                              + expInterest
                              + ", status="
                              + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                              + ")");
                }
                continue; // Skip if not interested
              }

              // Also check if experience is upcoming (current_time <= experience_time)
              // This means: include if experience start time >= current time
              if (experience.getExperienceDate() != null) {
                java.time.Instant now = java.time.Instant.now();
                java.time.ZoneId utc = java.time.ZoneId.of("UTC");

                if (experience.getStartTime() != null) {
                  // Both date and time provided - combine them
                  java.time.LocalDateTime experienceStartDateTime =
                      experience.getExperienceDate().atTime(experience.getStartTime());
                  // Convert to Instant using UTC timezone (Lambda runs in UTC)
                  java.time.Instant experienceStartInstant =
                      experienceStartDateTime.atZone(utc).toInstant();

                  // Include if experience start time >= current time (current_time <=
                  // experience_time)
                  if (experienceStartInstant.isBefore(now)) {
                    // Experience has already started (experience_time < current_time), skip
                    if (context != null && context.getLogger() != null) {
                      context
                          .getLogger()
                          .log(
                              "DEBUG: Skipping experience ["
                                  + experienceId
                                  + "] - experience has already started (experienceDate="
                                  + experience.getExperienceDate()
                                  + ", startTime="
                                  + experience.getStartTime()
                                  + ", experienceStart="
                                  + experienceStartInstant
                                  + ", now="
                                  + now
                                  + ")");
                    }
                    continue;
                  }
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log(
                            "DEBUG: Experience ["
                                + experienceId
                                + "] is upcoming or current - including (experienceDate="
                                + experience.getExperienceDate()
                                + ", startTime="
                                + experience.getStartTime()
                                + ", experienceStart="
                                + experienceStartInstant
                                + ", now="
                                + now
                                + ")");
                  }
                } else {
                  // Only date provided, no time - check if date >= today
                  java.time.LocalDate today = java.time.LocalDate.now(utc);
                  if (experience.getExperienceDate().isBefore(today)) {
                    // Experience date is in the past, skip
                    if (context != null && context.getLogger() != null) {
                      context
                          .getLogger()
                          .log(
                              "DEBUG: Skipping experience ["
                                  + experienceId
                                  + "] - experience date is in the past (experienceDate="
                                  + experience.getExperienceDate()
                                  + ", today="
                                  + today
                                  + ")");
                    }
                    continue;
                  }
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log(
                            "DEBUG: Experience ["
                                + experienceId
                                + "] date is today or future - including (experienceDate="
                                + experience.getExperienceDate()
                                + ", today="
                                + today
                                + ")");
                  }
                }
              } else {
                // No date, skip (can't determine if upcoming)
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log("DEBUG: Skipping experience [" + experienceId + "] - no experienceDate");
                }
                continue;
              }

              if (context != null && context.getLogger() != null) {
                context
                    .getLogger()
                    .log(
                        "DEBUG: Including experience ["
                            + experienceId
                            + "] - expInterest is true and experience is upcoming");
              }
            }

            // Filter by past experiences (ATTENDED and experienceDate + startTime < now)
            boolean pastFilter =
                past != null && ("true".equalsIgnoreCase(past) || "1".equals(past));

            // If no filters specified, default to past attended experiences
            // (interestedFilter is defined earlier, upcomingFilter is defined later)
            boolean hasAnyFilter =
                interestedFilter
                    || pastFilter
                    || (upcoming != null
                        && ("true".equalsIgnoreCase(upcoming) || "1".equals(upcoming)));
            if (!hasAnyFilter) {
              pastFilter = true;
            }

            if (pastFilter) {
              // Check if user has attended - prioritize paid=true field, then paymentDetails, then
              // status
              // If user has paid, they are considered to have attended the experience
              boolean hasAttended = false;
              if (ue.getPaid() != null && ue.getPaid()) {
                // Primary check: paid boolean field
                hasAttended = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: User has paid=true for experience ["
                              + experienceId
                              + "] - considering as attended");
                }
              } else if (ue.getPaymentDetails() != null) {
                // Secondary check: paymentDetails exists
                hasAttended = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: User has paymentDetails for experience ["
                              + experienceId
                              + "] - considering as attended");
                }
              } else if (ue.getStatus() == UserExperience.UserExperienceStatus.PAID
                  || ue.getStatus() == UserExperience.UserExperienceStatus.ATTENDED) {
                // Tertiary check: status is PAID or ATTENDED (ignore invalid statuses like ACTIVE)
                hasAttended = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: User has PAID/ATTENDED status for experience ["
                              + experienceId
                              + "] - considering as attended");
                }
              }

              if (!hasAttended) {
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: Skipping experience ["
                              + experienceId
                              + "] - user has not attended (status="
                              + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                              + ", paid="
                              + (ue.getPaid() != null ? ue.getPaid() : "null")
                              + ", hasPaymentDetails="
                              + (ue.getPaymentDetails() != null)
                              + ")");
                }
                continue;
              }

              // Check if experience date/time is in the past
              if (experience.getExperienceDate() != null) {
                java.time.Instant now = java.time.Instant.now();
                java.time.ZoneId utc = java.time.ZoneId.of("UTC");

                if (experience.getStartTime() != null) {
                  // Both date and time provided - combine them
                  java.time.LocalDateTime experienceStartDateTime =
                      experience.getExperienceDate().atTime(experience.getStartTime());
                  // Convert to Instant using UTC timezone (Lambda runs in UTC)
                  java.time.Instant experienceStartInstant =
                      experienceStartDateTime.atZone(utc).toInstant();

                  // Include if experience start time < current time (experience is in the past)
                  if (!experienceStartInstant.isBefore(now)) {
                    // Experience hasn't started yet or is currently happening, skip
                    if (context != null && context.getLogger() != null) {
                      context
                          .getLogger()
                          .log(
                              "DEBUG: Skipping experience ["
                                  + experienceId
                                  + "] - experience hasn't started yet (experienceDate="
                                  + experience.getExperienceDate()
                                  + ", startTime="
                                  + experience.getStartTime()
                                  + ", experienceStart="
                                  + experienceStartInstant
                                  + ", now="
                                  + now
                                  + ")");
                    }
                    continue;
                  }
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log(
                            "DEBUG: Experience ["
                                + experienceId
                                + "] is in the past - including (experienceDate="
                                + experience.getExperienceDate()
                                + ", startTime="
                                + experience.getStartTime()
                                + ", experienceStart="
                                + experienceStartInstant
                                + ", now="
                                + now
                                + ")");
                  }
                } else {
                  // Only date provided, no time - check if date < today
                  java.time.LocalDate today = java.time.LocalDate.now(utc);
                  if (!experience.getExperienceDate().isBefore(today)) {
                    // Experience date is today or in the future, skip
                    if (context != null && context.getLogger() != null) {
                      context
                          .getLogger()
                          .log(
                              "DEBUG: Skipping experience ["
                                  + experienceId
                                  + "] - experience date is not in the past (experienceDate="
                                  + experience.getExperienceDate()
                                  + ", today="
                                  + today
                                  + ")");
                    }
                    continue;
                  }
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log(
                            "DEBUG: Experience ["
                                + experienceId
                                + "] date is in the past - including (experienceDate="
                                + experience.getExperienceDate()
                                + ", today="
                                + today
                                + ")");
                  }
                }
              } else {
                // No date, can't determine if past
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log("DEBUG: Skipping experience [" + experienceId + "] - no experienceDate");
                }
                continue;
              }

              if (context != null && context.getLogger() != null) {
                context
                    .getLogger()
                    .log(
                        "DEBUG: Including experience ["
                            + experienceId
                            + "] - user has attended (status="
                            + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                            + ") and experience is in the past");
              }
            }

            // Filter by upcoming paid experiences:
            // - User has paid (status = PAID OR paymentDetails EXISTS)
            // - current_time < experienceStartTime (experienceDate + startTime)
            boolean upcomingFilter =
                upcoming != null && ("true".equalsIgnoreCase(upcoming) || "1".equals(upcoming));
            if (upcomingFilter) {
              // Check if user has paid - prioritize paid=true field, then paymentDetails, then
              // status
              boolean hasPaid = false;
              if (ue.getPaid() != null && ue.getPaid()) {
                // Primary check: paid boolean field
                hasPaid = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log("DEBUG: User has paid=true for experience [" + experienceId + "]");
                }
              } else if (ue.getPaymentDetails() != null) {
                // Secondary check: paymentDetails exists
                hasPaid = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log("DEBUG: User has paymentDetails for experience [" + experienceId + "]");
                }
              } else if (ue.getStatus() == UserExperience.UserExperienceStatus.PAID
                  || ue.getStatus() == UserExperience.UserExperienceStatus.ATTENDED) {
                // Tertiary check: status is PAID or ATTENDED (ignore invalid statuses like ACTIVE)
                hasPaid = true;
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: User has PAID/ATTENDED status for experience ["
                              + experienceId
                              + "]");
                }
              }

              if (!hasPaid) {
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: Skipping experience ["
                              + experienceId
                              + "] - user has not paid (status="
                              + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                              + ", paid="
                              + (ue.getPaid() != null ? ue.getPaid() : "null")
                              + ", paymentDetails="
                              + (ue.getPaymentDetails() != null ? "exists" : "null")
                              + ")");
                }
                continue; // User hasn't paid, skip
              }

              // Check if experience start time is in the future
              if (experience.getExperienceDate() != null) {
                java.time.Instant now = java.time.Instant.now();
                java.time.ZoneId utc = java.time.ZoneId.of("UTC");

                if (experience.getStartTime() != null) {
                  // Both date and time provided - combine them
                  java.time.LocalDateTime experienceStartDateTime =
                      experience.getExperienceDate().atTime(experience.getStartTime());
                  // Convert to Instant using UTC timezone (Lambda runs in UTC)
                  java.time.Instant experienceStartInstant =
                      experienceStartDateTime.atZone(utc).toInstant();

                  // Include only if experience start time is in the future (now < experienceStart)
                  if (!now.isBefore(experienceStartInstant)) {
                    if (context != null && context.getLogger() != null) {
                      context
                          .getLogger()
                          .log(
                              "DEBUG: Skipping experience ["
                                  + experienceId
                                  + "] - experience has already started or passed (experienceDate="
                                  + experience.getExperienceDate()
                                  + ", startTime="
                                  + experience.getStartTime()
                                  + ", experienceStart="
                                  + experienceStartInstant
                                  + ", now="
                                  + now
                                  + ")");
                    }
                    continue; // Experience has already started or passed
                  }
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log(
                            "DEBUG: Experience ["
                                + experienceId
                                + "] is upcoming and user has paid - including (experienceDate="
                                + experience.getExperienceDate()
                                + ", startTime="
                                + experience.getStartTime()
                                + ", experienceStart="
                                + experienceStartInstant
                                + ", now="
                                + now
                                + ")");
                  }
                } else {
                  // Only date provided, no time - check if date >= today
                  java.time.LocalDate today = java.time.LocalDate.now(utc);
                  if (experience.getExperienceDate().isBefore(today)) {
                    // Experience date is in the past, skip
                    if (context != null && context.getLogger() != null) {
                      context
                          .getLogger()
                          .log(
                              "DEBUG: Skipping experience ["
                                  + experienceId
                                  + "] - experience date is in the past (experienceDate="
                                  + experience.getExperienceDate()
                                  + ", today="
                                  + today
                                  + ")");
                    }
                    continue;
                  }
                  if (context != null && context.getLogger() != null) {
                    context
                        .getLogger()
                        .log(
                            "DEBUG: Experience ["
                                + experienceId
                                + "] date is today or future and user has paid - including (experienceDate="
                                + experience.getExperienceDate()
                                + ", today="
                                + today
                                + ")");
                  }
                }
              } else {
                // No date, can't determine if upcoming
                if (context != null && context.getLogger() != null) {
                  context
                      .getLogger()
                      .log(
                          "DEBUG: Skipping experience ["
                              + experienceId
                              + "] - no experienceDate (cannot determine if upcoming)");
                }
                continue;
              }
            }

            Map<String, Object> expData = experienceToMap(experience);
            expData.put("exp-interest", ue.getExpInterest());
            if (ue.getInterestScore() != null) {
              expData.put("interestScore", ue.getInterestScore());
            }
            expData.put("status", ue.getStatus() != null ? ue.getStatus().getValue() : null);
            expData.put("userStatus", ue.getStatus() != null ? ue.getStatus().getValue() : null);
            if (ue.getPaid() != null) {
              expData.put("paid", ue.getPaid());
            }
            expData.put("paymentDetails", ue.getPaymentDetails());
            filteredExperiences.add(expData);
            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log(
                      "DEBUG: Added experience ["
                          + experienceId
                          + "] to filtered results - status=["
                          + (ue.getStatus() != null ? ue.getStatus().getValue() : "null")
                          + "], paid=["
                          + (ue.getPaid() != null ? ue.getPaid() : "null")
                          + "], hasPaymentDetails=["
                          + (ue.getPaymentDetails() != null)
                          + "]");
            }
          } else {
            if (context != null && context.getLogger() != null) {
              context
                  .getLogger()
                  .log("DEBUG: Experience [" + experienceId + "] not found in Experience table");
            }
          }
        }
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log(
                  "DEBUG: Final filtered experiences count: "
                      + filteredExperiences.size()
                      + " out of "
                      + userExperiences.size()
                      + " UserExperience records");
        }
        experiences = filteredExperiences;
      } else if (groupId != null && !groupId.isBlank()) {
        // Query GroupExperience table for groupId
        List<GroupExperience> groupExperiences = groupExperienceDao.findByGroupId(groupId);
        for (GroupExperience ge : groupExperiences) {
          String experienceId = ge.getExperienceId();
          if (experienceId != null && !experienceId.isBlank()) {
            Optional<Experience> expOpt = experienceDao.findByExperienceId(experienceId);
            if (expOpt.isPresent()) {
              experiences.add(experienceToMap(expOpt.get()));
            }
          }
        }
      } else {
        // Return empty list - querying all experiences would be expensive
        // In production, consider pagination or other filtering
        experiences = new ArrayList<>();
      }

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "Experiences retrieved successfully");
      responseData.put("data", experiences);
      responseData.put("timestamp", Instant.now().toString());
      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Get nearby experiences using geohashing. Queries 9 neighboring geohash cells and filters by
   * distance. Optionally filters by radius in kilometers.
   *
   * @param latitude User's latitude
   * @param longitude User's longitude
   * @param radiusKm Optional radius in kilometers. If null, no radius filtering is applied.
   * @param context Lambda context
   * @return List of nearby experiences with distance information
   */
  private List<Map<String, Object>> getNearbyExperiences(
      double latitude, double longitude, Double radiusKm, Context context) {
    List<Map<String, Object>> nearbyExperiences = new ArrayList<>();
    try {
      // Get 9 neighboring geohash cells
      List<String> geohashes = GeohashUtil.getNeighboringGeohashes(latitude, longitude);

      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Querying geohashes: " + geohashes);
      }

      // Query venues in all 9 geohash cells (using DAO)
      List<VenueLocation> venues = venueLocationDao.findByGeohashPrefixes(geohashes);

      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Found " + venues.size() + " venues in geohash cells");
      }

      // Get experiences for each venue
      Map<String, List<Experience>> venueExperiencesMap = new HashMap<>();
      for (VenueLocation venue : venues) {
        List<Experience> experiences = experienceDao.findByVenueId(venue.getVenueId());
        if (context != null && context.getLogger() != null) {
          context
              .getLogger()
              .log("Venue " + venue.getVenueId() + " has " + experiences.size() + " experiences");
        }
        venueExperiencesMap.put(venue.getVenueId(), experiences);
      }

      // Build response with distance calculation
      for (VenueLocation venue : venues) {
        List<Experience> experiences = venueExperiencesMap.get(venue.getVenueId());
        for (Experience experience : experiences) {
          if (experience.getLatitude() != null && experience.getLongitude() != null) {
            double distance =
                GeohashUtil.calculateDistance(
                    latitude, longitude, experience.getLatitude(), experience.getLongitude());

            // Filter by radius if specified
            if (radiusKm != null && distance > radiusKm) {
              continue; // Skip experiences outside the radius
            }

            Map<String, Object> expData = new HashMap<>();
            expData.put("experienceId", experience.getExperienceId());
            expData.put("title", experience.getTitle());
            expData.put("description", experience.getDescription());
            expData.put("type", experience.getType());
            expData.put("latitude", experience.getLatitude());
            expData.put("longitude", experience.getLongitude());
            expData.put("address", experience.getAddress());
            expData.put("city", experience.getCity());
            expData.put("distanceKm", Math.round(distance * 100.0) / 100.0); // Round to 2 decimals
            expData.put("venueId", venue.getVenueId());
            expData.put("venueName", venue.getName());
            nearbyExperiences.add(expData);
          }
        }
      }

      // Sort by distance (closest first)
      nearbyExperiences.sort(
          (e1, e2) -> {
            Double dist1 = (Double) e1.get("distanceKm");
            Double dist2 = (Double) e2.get("distanceKm");
            if (dist1 == null || dist2 == null) {
              return 0;
            }
            return Double.compare(dist1, dist2);
          });

    } catch (Exception e) {
      if (context != null && context.getLogger() != null) {
        context.getLogger().log("Error getting nearby experiences: " + e.getMessage());
      }
    }
    return nearbyExperiences;
  }

  /** Handle PUT /experiences/{experienceId} - Create, update, or delete experience. */
  private APIGatewayProxyResponseEvent handleCreateOrUpdateExperience(
      String experienceId, String body, String userId, Context context) {
    try {
      // Check if experience exists
      Optional<Experience> existingOpt = experienceDao.findByExperienceId(experienceId);

      // Handle DELETE operation
      // Delete if: 1) body has "action": "delete", 2) body has "status": "DELETED", or 3) body is
      // empty/null and experience exists
      boolean shouldDelete = false;
      Map<String, Object> requestMap = null;

      if (body == null || body.isBlank()) {
        // Empty body with existing experience = delete
        if (existingOpt.isPresent()) {
          shouldDelete = true;
        } else {
          return createErrorResponse(
              400, "Bad Request", "Request body is required for creating new experience");
        }
      } else {
        // Parse request body
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedMap = objectMapper.readValue(body, Map.class);
        requestMap = parsedMap;

        // Check for delete action
        if ("delete".equalsIgnoreCase((String) requestMap.get("action"))
            || "DELETED".equals(requestMap.get("status"))
            || Experience.ExperienceStatus.DELETED.getValue().equals(requestMap.get("status"))) {
          shouldDelete = true;
        }
      }

      // Perform delete operation
      if (shouldDelete) {
        if (existingOpt.isEmpty()) {
          return createErrorResponse(404, "Not Found", "Experience not found: " + experienceId);
        }

        Experience experience = existingOpt.get();

        // Verify user owns the experience
        if (!userId.equals(experience.getCreatedBy())) {
          return createErrorResponse(403, "Forbidden", "You can only delete your own experiences");
        }

        // Soft delete by setting status to DELETED
        experience.setStatus(Experience.ExperienceStatus.DELETED);
        experience.setUpdatedAt(Instant.now());
        experience = experienceDao.save(experience);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Experience deleted successfully");
        responseData.put("data", experienceToMap(experience));
        responseData.put("timestamp", Instant.now().toString());
        return createSuccessResponse(200, responseData, "application/json");
      }

      // Handle CREATE or UPDATE operation
      if (requestMap == null) {
        return createErrorResponse(400, "Bad Request", "Request body is required");
      }

      Experience experience;

      if (existingOpt.isPresent()) {
        // Update existing
        experience = existingOpt.get();

        // Verify user owns the experience (unless it's a new field update)
        if (!userId.equals(experience.getCreatedBy())) {
          return createErrorResponse(403, "Forbidden", "You can only update your own experiences");
        }
      } else {
        // Create new
        experience = new Experience(userId);
        experience.setExperienceId(experienceId);
      }

      // Update fields from request
      if (requestMap.containsKey("title")) {
        experience.setTitle((String) requestMap.get("title"));
      }
      if (requestMap.containsKey("description")) {
        experience.setDescription((String) requestMap.get("description"));
      }
      if (requestMap.containsKey("type")) {
        experience.setType(Experience.ExperienceType.fromValue((String) requestMap.get("type")));
      }
      if (requestMap.containsKey("latitude")) {
        experience.setLatitude(((Number) requestMap.get("latitude")).doubleValue());
      }
      if (requestMap.containsKey("longitude")) {
        experience.setLongitude(((Number) requestMap.get("longitude")).doubleValue());
      }
      if (requestMap.containsKey("experienceDate")) {
        experience.setExperienceDate(LocalDate.parse((String) requestMap.get("experienceDate")));
      }
      if (requestMap.containsKey("startTime")) {
        experience.setStartTime(LocalTime.parse((String) requestMap.get("startTime")));
      }
      if (requestMap.containsKey("endTime")) {
        experience.setEndTime(LocalTime.parse((String) requestMap.get("endTime")));
      }
      if (requestMap.containsKey("pricePerPerson")) {
        experience.setPricePerPerson(
            new java.math.BigDecimal(((Number) requestMap.get("pricePerPerson")).toString()));
      }
      if (requestMap.containsKey("currency")) {
        experience.setCurrency((String) requestMap.get("currency"));
      }
      if (requestMap.containsKey("address")) {
        experience.setAddress((String) requestMap.get("address"));
      }
      if (requestMap.containsKey("city")) {
        experience.setCity((String) requestMap.get("city"));
      }
      if (requestMap.containsKey("country")) {
        experience.setCountry((String) requestMap.get("country"));
      }
      if (requestMap.containsKey("location")) {
        experience.setLocation((String) requestMap.get("location"));
      }
      if (requestMap.containsKey("status")) {
        Object statusObj = requestMap.get("status");
        if (statusObj instanceof String) {
          experience.setStatus(Experience.ExperienceStatus.fromValue((String) statusObj));
        }
      }
      if (requestMap.containsKey("maxCapacity")) {
        experience.setMaxCapacity(((Number) requestMap.get("maxCapacity")).intValue());
      }
      if (requestMap.containsKey("tags")) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) requestMap.get("tags");
        experience.setTags(tags);
      }
      if (requestMap.containsKey("images")) {
        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) requestMap.get("images");
        experience.setImages(images);
      }
      if (requestMap.containsKey("contactInfo")) {
        experience.setContactInfo((String) requestMap.get("contactInfo"));
      }
      if (requestMap.containsKey("requirements")) {
        experience.setRequirements((String) requestMap.get("requirements"));
      }
      if (requestMap.containsKey("cancellationPolicy")) {
        experience.setCancellationPolicy((String) requestMap.get("cancellationPolicy"));
      }
      // Add more field mappings as needed

      // Save experience
      experience = experienceDao.save(experience);

      // Automatically create/update VenueLocation if experience has coordinates
      if (experience.getLatitude() != null && experience.getLongitude() != null) {
        try {
          // Generate or use provided venueId
          String venueId;
          if (requestMap.containsKey("venueId") && requestMap.get("venueId") != null) {
            venueId = (String) requestMap.get("venueId");
          } else {
            // Generate venueId from experienceId (or use experienceId as venueId)
            venueId = "venue-" + experienceId;
          }

          // Check if venue already exists
          Optional<VenueLocation> existingVenueOpt = venueLocationDao.findByVenueId(venueId);
          VenueLocation venue;

          if (existingVenueOpt.isPresent()) {
            // Update existing venue
            venue = existingVenueOpt.get();
          } else {
            // Create new venue
            venue = new VenueLocation(venueId);
          }

          // Update venue details from experience
          if (experience.getLocation() != null) {
            venue.setName(experience.getLocation());
          } else if (experience.getTitle() != null) {
            venue.setName(experience.getTitle());
          }
          venue.setLatitude(experience.getLatitude());
          venue.setLongitude(experience.getLongitude());
          if (experience.getAddress() != null) {
            venue.setAddress(experience.getAddress());
          }
          if (experience.getCity() != null) {
            venue.setCity(experience.getCity());
          }
          if (experience.getCountry() != null) {
            venue.setCountry(experience.getCountry());
          }

          // Save venue location (geohash will be calculated automatically by DAO)
          venueLocationDao.save(venue);

          // Update experience with venueId for GSI1 queries
          // We need to save the experience again with venueId
          // Since Experience model doesn't have venueId, we'll save it directly to DynamoDB
          // Use entity type prefix for single-table design
          String experiencePk = "EXPERIENCE#" + experience.getExperienceId();
          Map<String, AttributeValue> experienceKey = new HashMap<>();
          experienceKey.put(
              "userId", AttributeValue.builder().s(experiencePk).build()); // userId is the PK field
          experienceKey.put(
              "createdAt",
              AttributeValue.builder().s(experience.getCreatedAt().toString()).build());

          Map<String, AttributeValue> updateValues = new HashMap<>();
          updateValues.put(":venueId", AttributeValue.builder().s(venueId).build());
          updateValues.put(
              ":experienceId", AttributeValue.builder().s(experience.getExperienceId()).build());

          Map<String, String> expressionNames = new HashMap<>();
          expressionNames.put("#venueId", "venueId");
          expressionNames.put("#GSI1PK", "GSI1PK");
          expressionNames.put("#GSI1SK", "GSI1SK");

          software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest updateRequest =
              software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest.builder()
                  .tableName(TABLE_NAME)
                  .key(experienceKey)
                  .updateExpression(
                      "SET #venueId = :venueId, #GSI1PK = :venueId, #GSI1SK = :experienceId")
                  .expressionAttributeNames(expressionNames)
                  .expressionAttributeValues(updateValues)
                  .build();

          dynamoDbClient.updateItem(updateRequest);

          if (context != null && context.getLogger() != null) {
            context.getLogger().log("VenueLocation created/updated: " + venueId);
          }
        } catch (Exception e) {
          // Log error but don't fail the experience creation
          if (context != null && context.getLogger() != null) {
            context.getLogger().log("Warning: Failed to create VenueLocation: " + e.getMessage());
            e.printStackTrace();
          }
        }
      }

      // Build response
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      String message =
          existingOpt.isPresent()
              ? "Experience updated successfully"
              : "Experience created successfully";
      responseData.put("message", message);
      responseData.put("data", experienceToMap(experience));
      responseData.put("timestamp", Instant.now().toString());
      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error processing request: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle PUT /users/{userId}/experiences/{experienceId}/interest - Mark user as interested in
   * experience.
   */
  private APIGatewayProxyResponseEvent handleMarkUserInterest(
      String userId, String experienceId, String body, Context context) {
    try {
      // Verify experience exists
      Optional<Experience> experienceOpt = experienceDao.findByExperienceId(experienceId);
      if (experienceOpt.isEmpty()) {
        return createErrorResponse(404, "Not Found", "Experience not found: " + experienceId);
      }

      // Check if UserExperience already exists
      List<UserExperience> existingUserExperiences = userExperienceDao.findByUserId(userId);
      UserExperience userExperience = null;
      for (UserExperience ue : existingUserExperiences) {
        if (experienceId.equals(ue.getExperienceId())) {
          userExperience = ue;
          break;
        }
      }

      // Create new or update existing
      if (userExperience == null) {
        userExperience = new UserExperience(userId, experienceId);
      }

      // Parse interested boolean from body (1/true = interested, 0/false = not interested)
      boolean isInterested = true; // Default to interested if not specified
      if (body != null && !body.isBlank()) {
        try {
          Map<String, Object> requestMap =
              objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});

          // Check for "interested" field (boolean, 1/0, or "true"/"false")
          if (requestMap.containsKey("interested")) {
            Object interestedObj = requestMap.get("interested");
            if (interestedObj instanceof Boolean) {
              isInterested = (Boolean) interestedObj;
            } else if (interestedObj instanceof Number) {
              // Accept 1 for true, 0 for false
              isInterested = ((Number) interestedObj).intValue() == 1;
            } else if (interestedObj instanceof String) {
              String interestedStr = ((String) interestedObj).trim().toLowerCase();
              isInterested = "true".equals(interestedStr) || "1".equals(interestedStr);
            }
          }

          // Legacy support: if interestScore is provided, treat it as interested if > 0
          // (for backward compatibility)
          if (!requestMap.containsKey("interested") && requestMap.containsKey("interestScore")) {
            Object scoreObj = requestMap.get("interestScore");
            if (scoreObj instanceof Number) {
              double score = ((Number) scoreObj).doubleValue();
              isInterested = score > 0;
              userExperience.setInterestScore(score);
            } else if (scoreObj instanceof String) {
              double score = Double.parseDouble((String) scoreObj);
              isInterested = score > 0;
              userExperience.setInterestScore(score);
            }
          }
        } catch (Exception e) {
          // If body parsing fails, continue with default values
          if (context != null && context.getLogger() != null) {
            context.getLogger().log("Warning: Could not parse request body: " + e.getMessage());
          }
        }
      }

      // Set exp-interest field (not status)
      userExperience.setExpInterest(isInterested);

      // Save UserExperience
      userExperienceDao.save(userExperience);

      // Build response
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      String message =
          isInterested ? "User interest marked successfully" : "User interest removed successfully";
      responseData.put("message", message);
      Map<String, Object> data = new HashMap<>();
      data.put("userId", userId);
      data.put("experienceId", experienceId);
      data.put("interested", isInterested);
      data.put("exp-interest", isInterested); // Also include the DynamoDB field name
      if (userExperience.getStatus() != null) {
        data.put("status", userExperience.getStatus().getValue());
      }
      if (userExperience.getInterestScore() != null) {
        data.put("interestScore", userExperience.getInterestScore());
      }
      responseData.put("data", data);
      responseData.put("timestamp", Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error marking user interest: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /**
   * Handle PUT /users/{userId}/experiences/{experienceId}/payment - Mark user as paid for
   * experience.
   */
  private APIGatewayProxyResponseEvent handleMarkUserPayment(
      String userId, String experienceId, String body, Context context) {
    try {
      // Verify experience exists
      Optional<Experience> experienceOpt = experienceDao.findByExperienceId(experienceId);
      if (experienceOpt.isEmpty()) {
        return createErrorResponse(404, "Not Found", "Experience not found: " + experienceId);
      }

      // Check if UserExperience already exists
      List<UserExperience> existingUserExperiences = userExperienceDao.findByUserId(userId);
      UserExperience userExperience = null;
      for (UserExperience ue : existingUserExperiences) {
        if (experienceId.equals(ue.getExperienceId())) {
          userExperience = ue;
          break;
        }
      }

      // Create new or update existing
      if (userExperience == null) {
        userExperience = new UserExperience(userId, experienceId);
      }

      // Parse request body
      if (body != null && !body.isBlank()) {
        try {
          Map<String, Object> requestMap =
              objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});

          // Set status if provided
          if (requestMap.containsKey("status")) {
            Object statusObj = requestMap.get("status");
            String statusStr = statusObj != null ? String.valueOf(statusObj).toUpperCase() : null;
            if ("PAID".equals(statusStr)) {
              userExperience.setStatus(UserExperience.UserExperienceStatus.PAID);
            } else {
              // Allow other statuses too (ATTENDED, CANCELLED, etc.)
              userExperience.setStatus(UserExperience.UserExperienceStatus.fromValue(statusStr));
            }
          } else {
            // If status not provided but paymentDetails exists, set status to PAID
            if (requestMap.containsKey("paymentDetails")) {
              userExperience.setStatus(UserExperience.UserExperienceStatus.PAID);
            }
          }

          // Set payment details if provided
          if (requestMap.containsKey("paymentDetails")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentMap = (Map<String, Object>) requestMap.get("paymentDetails");

            UserExperience.PaymentDetails paymentDetails = new UserExperience.PaymentDetails();

            if (paymentMap.containsKey("amount")) {
              Object amountObj = paymentMap.get("amount");
              if (amountObj instanceof Number) {
                paymentDetails.setAmount(BigDecimal.valueOf(((Number) amountObj).doubleValue()));
              } else if (amountObj instanceof String) {
                paymentDetails.setAmount(new BigDecimal((String) amountObj));
              }
            }

            if (paymentMap.containsKey("currency")) {
              paymentDetails.setCurrency(String.valueOf(paymentMap.get("currency")));
            }

            if (paymentMap.containsKey("paymentMethod")) {
              paymentDetails.setPaymentMethod(String.valueOf(paymentMap.get("paymentMethod")));
            }

            if (paymentMap.containsKey("paymentDate")) {
              Object dateObj = paymentMap.get("paymentDate");
              if (dateObj instanceof String) {
                paymentDetails.setPaymentDate(Instant.parse((String) dateObj));
              }
            } else {
              // Default to current time if not provided
              paymentDetails.setPaymentDate(Instant.now());
            }

            if (paymentMap.containsKey("transactionId")) {
              paymentDetails.setTransactionId(String.valueOf(paymentMap.get("transactionId")));
            }

            userExperience.setPaymentDetails(paymentDetails);

            // If paymentDetails exists but status wasn't set, set it to PAID
            if (userExperience.getStatus() != UserExperience.UserExperienceStatus.PAID) {
              userExperience.setStatus(UserExperience.UserExperienceStatus.PAID);
            }
          }
        } catch (Exception e) {
          if (context != null && context.getLogger() != null) {
            context.getLogger().log("Warning: Could not parse request body: " + e.getMessage());
          }
          // If body parsing fails, at least set status to PAID
          userExperience.setStatus(UserExperience.UserExperienceStatus.PAID);
        }
      } else {
        // No body provided, just set status to PAID
        userExperience.setStatus(UserExperience.UserExperienceStatus.PAID);
      }

      // Save UserExperience
      userExperienceDao.save(userExperience);

      // Build response
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("success", true);
      responseData.put("message", "User marked as paid successfully");
      Map<String, Object> data = new HashMap<>();
      data.put("userId", userId);
      data.put("experienceId", experienceId);
      if (userExperience.getStatus() != null) {
        data.put("status", userExperience.getStatus().getValue());
      }
      if (userExperience.getPaymentDetails() != null) {
        data.put("paymentDetails", userExperience.getPaymentDetails());
      }
      responseData.put("data", data);
      responseData.put("timestamp", Instant.now().toString());

      return createSuccessResponse(200, responseData, "application/json");
    } catch (Exception e) {
      String errorMessage = "Error marking user payment: " + e.getMessage();
      if (context != null && context.getLogger() != null) {
        context.getLogger().log(errorMessage);
        e.printStackTrace();
      }
      return createErrorResponse(500, "Internal server error", errorMessage);
    }
  }

  /** Convert Experience to Map for JSON response. */
  private Map<String, Object> experienceToMap(Experience experience) {
    Map<String, Object> map = new HashMap<>();
    map.put("experienceId", experience.getExperienceId());
    map.put("title", experience.getTitle());
    map.put("description", experience.getDescription());
    map.put("type", experience.getType());
    map.put("status", experience.getStatus());
    map.put("location", experience.getLocation());
    map.put("address", experience.getAddress());
    map.put("city", experience.getCity());
    map.put("country", experience.getCountry());
    map.put("latitude", experience.getLatitude());
    map.put("longitude", experience.getLongitude());
    map.put("experienceDate", experience.getExperienceDate());
    map.put("startTime", experience.getStartTime());
    map.put("endTime", experience.getEndTime());
    map.put("pricePerPerson", experience.getPricePerPerson());
    map.put("currency", experience.getCurrency());
    map.put("maxCapacity", experience.getMaxCapacity());
    map.put("currentBookings", experience.getCurrentBookings());
    map.put("createdAt", experience.getCreatedAt());
    map.put("updatedAt", experience.getUpdatedAt());
    return map;
  }

  /** Creates a successful HTTP response. */
  private APIGatewayProxyResponseEvent createSuccessResponse(
      int statusCode, Object body, String contentType) {
    try {
      APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setStatusCode(statusCode);

      Map<String, String> headers = new HashMap<>();
      headers.put("Content-Type", contentType);
      headers.put("X-Request-Id", java.util.UUID.randomUUID().toString());
      response.setHeaders(headers);

      String bodyString;
      if (body instanceof String) {
        bodyString = (String) body;
      } else {
        bodyString = objectWriter.writeValueAsString(body);
      }
      response.setBody(bodyString);

      return response;
    } catch (Exception e) {
      // Fallback if JSON serialization fails
      APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setStatusCode(statusCode);
      response.setBody("{\"success\":false,\"message\":\"Error serializing response\"}");
      return response;
    }
  }

  /** Creates an error HTTP response. */
  private APIGatewayProxyResponseEvent createErrorResponse(
      int statusCode, String message, String details) {
    try {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("message", message);
      if (details != null) {
        errorResponse.put("error", details);
      }
      errorResponse.put("timestamp", java.time.Instant.now().toString());

      return createSuccessResponse(statusCode, errorResponse, "application/json");
    } catch (Exception e) {
      // Fallback if JSON serialization fails
      APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
      response.setStatusCode(statusCode);
      response.setBody("{\"success\":false,\"message\":\"" + message + "\"}");
      return response;
    }
  }

  /**
   * Utility method to extract Cognito user ID from API Gateway event. This can be used by other
   * handlers or utility classes.
   */
  public static Optional<String> extractUserId(APIGatewayProxyRequestEvent event) {
    if (event == null || event.getHeaders() == null) {
      return Optional.empty();
    }
    String userId = getHeaderCaseInsensitive(event.getHeaders(), HEADER_COGNITO_IDENTITY);
    return (userId != null && !userId.isBlank()) ? Optional.of(userId) : Optional.empty();
  }

  /** Utility method to extract Cognito data from API Gateway event. */
  public static Optional<String> extractCognitoData(APIGatewayProxyRequestEvent event) {
    if (event == null || event.getHeaders() == null) {
      return Optional.empty();
    }
    String data = getHeaderCaseInsensitive(event.getHeaders(), HEADER_COGNITO_DATA);
    return (data != null && !data.isBlank()) ? Optional.of(data) : Optional.empty();
  }

  /**
   * Get header value with case-insensitive lookup. API Gateway normalizes headers to lowercase, but
   * test console might pass them differently.
   */
  private static String getHeaderCaseInsensitive(Map<String, String> headers, String headerName) {
    if (headers == null || headerName == null) {
      return null;
    }

    // Try exact match first (most common case)
    String value = headers.get(headerName);
    if (value != null) {
      return value;
    }

    // Try case-insensitive lookup
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(headerName)) {
        return entry.getValue();
      }
    }

    return null;
  }
}
