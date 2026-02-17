package com.yourafterspace.yas_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yourafterspace.yas_backend.dto.ApiResponse;
import com.yourafterspace.yas_backend.dto.ExperienceRequest;
import com.yourafterspace.yas_backend.dto.ExperienceResponse;
import com.yourafterspace.yas_backend.model.Experience.ExperienceStatus;
import com.yourafterspace.yas_backend.service.ExperienceService;
import com.yourafterspace.yas_backend.util.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST controller for experience management. */
@RestController
@RequestMapping("/v1")
public class ExperienceController {

  private static final Logger logger = LoggerFactory.getLogger(ExperienceController.class);

  private final UserContext userContext;
  private final ExperienceService experienceService;
  private final ObjectMapper objectMapper;

  public ExperienceController(
      UserContext userContext,
      ExperienceService experienceService,
      @Autowired(required = false) ObjectMapper objectMapper) {
    this.userContext = userContext;
    this.experienceService = experienceService;
    // Use provided ObjectMapper or create a default one
    if (objectMapper != null) {
      this.objectMapper = objectMapper;
    } else {
      // Fallback: create ObjectMapper with JavaTimeModule
      this.objectMapper = new ObjectMapper();
      this.objectMapper.registerModule(new JavaTimeModule());
    }
  }

  /**
   * Create a new experience.
   *
   * <p>POST /v1/experience
   */
  @PostMapping("/experience")
  public ResponseEntity<ExperienceResponse> createExperience(
      @Valid @RequestBody ExperienceRequest request) {
    logger.info("Creating new experience with title: {}", request.getTitle());

    String userId = userContext.requireCurrentUserId();
    ExperienceResponse response = experienceService.createExperience(request, userId);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Get an experience by ID.
   *
   * <p>GET /v1/experience/{experienceId}
   */
  @GetMapping("/experience/{experienceId}")
  public ResponseEntity<ExperienceResponse> getExperience(@PathVariable String experienceId) {
    logger.info("Getting experience: {}", experienceId);

    ExperienceResponse response = experienceService.getExperience(experienceId);
    return ResponseEntity.ok(response);
  }

  /**
   * Update experience and soft delete.
   *
   * <p>PATCH /v1/experience/{experienceId}
   */
  @PatchMapping("/experience/{experienceId}")
  public ResponseEntity<ExperienceResponse> updateExperience(
      @PathVariable String experienceId, @Valid @RequestBody ExperienceRequest request) {
    logger.info("Updating experience: {}", experienceId);

    String userId = userContext.requireCurrentUserId();
    ExperienceResponse response = experienceService.updateExperience(experienceId, request, userId);

    return ResponseEntity.ok(response);
  }

  /**
   * Mark or unmark the current user as interested in an experience.
   *
   * <p>PUT /v1/experiences/{experienceId}/interest - Body: {"interested": true} or {"interested":
   * false}
   */
  @PutMapping("/experiences/{experienceId}/interest")
  public ResponseEntity<ApiResponse<Map<String, Object>>> markExperienceInterest(
      @PathVariable String experienceId, @RequestBody(required = false) Map<String, Object> body) {
    String userId = userContext.requireCurrentUserId();
    boolean interested = true;
    if (body != null && body.containsKey("interested")) {
      Object v = body.get("interested");
      if (v instanceof Boolean) {
        interested = (Boolean) v;
      } else if (v instanceof String) {
        interested = "true".equalsIgnoreCase((String) v) || "1".equals(v);
      } else if (v instanceof Number) {
        interested = ((Number) v).intValue() == 1;
      }
    }
    experienceService.markInterested(userId, experienceId, interested);
    return ResponseEntity.ok(
        ApiResponse.success(
            interested ? "Marked as interested" : "Removed from interested",
            Map.of("experienceId", experienceId, "interested", interested)));
  }

  /**
   * Create, update, or delete experience (matches Lambda handler behavior).
   *
   * <p>PUT /v1/experiences/{experienceId} - Create or update experience
   *
   * <p>PUT /v1/experiences/{experienceId} with {"action": "delete"} - Delete experience
   *
   * <p>PUT /v1/experiences/{experienceId} with {"status": "DELETED"} - Delete experience
   *
   * <p>PUT /v1/experiences/{experienceId} with empty body (if exists) - Delete experience
   */
  @PutMapping("/experiences/{experienceId}")
  public ResponseEntity<?> createOrUpdateExperience(
      @PathVariable String experienceId,
      @RequestBody(required = false) Map<String, Object> requestMap) {
    logger.info("PUT /v1/experiences/{} - Create, update, or delete", experienceId);

    String userId = userContext.requireCurrentUserId();

    // Handle delete operations
    if (requestMap == null || requestMap.isEmpty()) {
      // Empty body - try to delete if exists
      try {
        // Check if experience exists
        experienceService.getExperience(experienceId);
        // If exists, create delete request
        ExperienceRequest deleteRequest = new ExperienceRequest();
        deleteRequest.setStatus(ExperienceStatus.DELETED);
        return ResponseEntity.ok(
            experienceService.updateExperience(experienceId, deleteRequest, userId));
      } catch (Exception e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Request body is required for creating new experience"));
      }
    }

    // Check for delete action
    Object actionObj = requestMap.get("action");
    Object statusObj = requestMap.get("status");

    if ("delete".equalsIgnoreCase(String.valueOf(actionObj))
        || "DELETED".equals(String.valueOf(statusObj))
        || ExperienceStatus.DELETED.getValue().equals(String.valueOf(statusObj))) {
      logger.info("Deleting experience: {}", experienceId);
      try {
        ExperienceRequest deleteRequest = new ExperienceRequest();
        deleteRequest.setStatus(ExperienceStatus.DELETED);
        ExperienceResponse response =
            experienceService.updateExperience(experienceId, deleteRequest, userId);
        return ResponseEntity.ok(response);
      } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("Experience not found: " + experienceId));
      }
    }

    // Convert Map to ExperienceRequest
    ExperienceRequest request;
    try {
      request = objectMapper.convertValue(requestMap, ExperienceRequest.class);
    } catch (Exception e) {
      logger.error("Error converting request map to ExperienceRequest", e);
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Invalid request body: " + e.getMessage()));
    }

    // Try to update first (if exists), otherwise create
    try {
      // Check if experience exists
      experienceService.getExperience(experienceId);
      // Experience exists, update it
      logger.info("Updating existing experience: {}", experienceId);
      ExperienceResponse response =
          experienceService.updateExperience(experienceId, request, userId);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      // Experience doesn't exist, create it
      logger.info("Creating new experience: {}", experienceId);
      try {
        ExperienceResponse response = experienceService.createExperience(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      } catch (Exception createEx) {
        logger.error("Error creating experience", createEx);
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Error creating experience: " + createEx.getMessage()));
      }
    }
  }

  /**
   * Get all experiences in the database (catalog / listing), optionally filtered by city.
   *
   * <p>GET /v1/experiences/all - Returns all experiences
   *
   * <p>GET /v1/experiences/all?city=Mumbai - Returns experiences in that city (case-sensitive)
   */
  @GetMapping("/experiences/all")
  public ResponseEntity<ApiResponse<List<ExperienceResponse>>> getAllExperiences(
      @RequestParam(required = false) String city) {
    List<ExperienceResponse> experiences;
    if (city != null && !city.isBlank()) {
      logger.info("Getting experiences for city: {}", city);
      experiences = experienceService.getExperiencesByCity(city.trim());
      return ResponseEntity.ok(
          ApiResponse.success("Experiences in " + city + " retrieved successfully", experiences));
    }
    logger.info("Getting all experiences from database");
    experiences = experienceService.getAllExperiences();
    return ResponseEntity.ok(
        ApiResponse.success("All experiences retrieved successfully", experiences));
  }

  /**
   * Get experiences based on filters.
   *
   * <p>GET /v1/experiences - Returns all past attended experiences by default
   *
   * <p>GET /v1/experiences?past=true - Explicitly request past attended experiences
   *
   * <p>GET /v1/experiences?upcoming=true - Request upcoming paid experiences
   *
   * @param past Query parameter to filter past attended experiences
   * @param upcoming Query parameter to filter upcoming paid experiences
   * @return List of experiences matching the filter
   */
  @GetMapping("/experiences")
  public ResponseEntity<ApiResponse<List<ExperienceResponse>>> getExperiences(
      @RequestParam(required = false) String past,
      @RequestParam(required = false) String upcoming) {
    String userId = userContext.requireCurrentUserId();

    // Check for upcoming experiences request
    if (upcoming != null && ("true".equalsIgnoreCase(upcoming) || "1".equals(upcoming))) {
      logger.info("Getting upcoming paid experiences for user: {}", userId);
      List<ExperienceResponse> upcomingExperiences =
          experienceService.getUpcomingPaidExperiences(userId);
      ApiResponse<List<ExperienceResponse>> response =
          ApiResponse.success("Upcoming experiences retrieved successfully", upcomingExperiences);
      return ResponseEntity.ok(response);
    }

    // Default behavior: return past attended experiences
    // If past is explicitly set to false, return empty list (for future
    // extensibility)
    boolean shouldReturnPast = true;
    if (past != null && ("false".equalsIgnoreCase(past) || "0".equals(past))) {
      shouldReturnPast = false;
    }

    if (shouldReturnPast) {
      logger.info("Getting past attended experiences for user: {}", userId);
      List<ExperienceResponse> pastExperiences =
          experienceService.getPastAttendedExperiences(userId);
      ApiResponse<List<ExperienceResponse>> response =
          ApiResponse.success("Past attended experiences retrieved successfully", pastExperiences);
      return ResponseEntity.ok(response);
    }

    // If explicitly set to false, return empty list
    logger.info("Past filter set to false for GET /v1/experiences, returning empty list");
    ApiResponse<List<ExperienceResponse>> response =
        ApiResponse.success("No experiences found", List.of());
    return ResponseEntity.ok(response);
  }

  /**
   * Get all experiences the current user has marked as interested.
   *
   * <p>GET /v1/user/interested-experiences
   */
  @GetMapping("/user/interested-experiences")
  public ResponseEntity<ApiResponse<List<ExperienceResponse>>> getInterestedExperiences() {
    String userId = userContext.requireCurrentUserId();
    List<ExperienceResponse> list = experienceService.getInterestedExperiences(userId);
    return ResponseEntity.ok(
        ApiResponse.success("Interested experiences retrieved successfully", list));
  }
}
