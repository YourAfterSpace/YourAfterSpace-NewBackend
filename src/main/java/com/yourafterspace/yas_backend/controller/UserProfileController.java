package com.yourafterspace.yas_backend.controller;

import com.yourafterspace.yas_backend.dto.ApiResponse;
import com.yourafterspace.yas_backend.dto.CategoryWithAnswersResponse;
import com.yourafterspace.yas_backend.dto.QuestionnaireAnswersRequest;
import com.yourafterspace.yas_backend.dto.QuestionnaireAnswersResponse;
import com.yourafterspace.yas_backend.dto.QuestionnaireProgressResponse;
import com.yourafterspace.yas_backend.dto.UserProfileRequest;
import com.yourafterspace.yas_backend.dto.UserProfileResponse;
import com.yourafterspace.yas_backend.dto.UserProfileUpdateRequest;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateRequest;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateResponse;
import com.yourafterspace.yas_backend.service.UserProfileService;
import com.yourafterspace.yas_backend.util.UserContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for user profile endpoints.
 *
 * <p>All endpoints in this controller require authentication via AWS API Gateway + Cognito. The
 * user identity is automatically extracted from the x-amzn-oidc-identity header by the
 * ApiGatewayAuthFilter, which is set by API Gateway after token validation.
 */
@RestController
@RequestMapping("/v1/user")
public class UserProfileController {

  private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

  private final UserContext userContext;
  private final UserProfileService userProfileService;

  public UserProfileController(UserContext userContext, UserProfileService userProfileService) {
    this.userContext = userContext;
    this.userProfileService = userProfileService;
  }

  /**
   * Create or update user profile.
   *
   * <p>This endpoint allows authenticated users to create or update their profile information
   * including date of birth, address, gender, profession, and other details.
   *
   * @param request User profile request containing profile data
   * @return User profile response with saved data
   */
  @PostMapping("/profile")
  public ResponseEntity<ApiResponse<UserProfileResponse>> createOrUpdateProfile(
      @Valid @RequestBody UserProfileRequest request) {
    String userId = userContext.requireCurrentUserId();
    logger.info("Creating or updating profile for userId: {}", userId);

    UserProfileResponse response = userProfileService.createOrUpdateProfile(userId, request);

    return ResponseEntity.ok(ApiResponse.success("User profile saved successfully", response));
  }

  /**
   * Get current user's profile.
   *
   * <p>This endpoint allows authenticated users to retrieve their profile information.
   *
   * @return User profile response with profile data
   */
  @GetMapping("/profile")
  public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
    String userId = userContext.requireCurrentUserId();
    logger.info("Fetching profile for userId: {}", userId);

    UserProfileResponse response = userProfileService.getProfile(userId);

    return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", response));
  }

  /**
   * Submit questionnaire answers. Keys must match question ids from GET /v1/questions. Values: one
   * string for SINGLE_CHOICE, list of strings for MULTIPLE_CHOICE.
   *
   * @param request Request containing answers map (e.g. {"gender": "Female", "interests":
   *     ["Travel", "Music"]})
   * @return Updated profile including saved answers
   */
  @PostMapping("/questionnaire")
  public ResponseEntity<ApiResponse<UserProfileResponse>> submitQuestionnaire(
      @Valid @RequestBody QuestionnaireAnswersRequest request) {
    String userId = userContext.requireCurrentUserId();
    logger.info("Submitting questionnaire answers for userId: {}", userId);

    UserProfileResponse response =
        userProfileService.saveQuestionnaireAnswers(userId, request.getAnswers());

    return ResponseEntity.ok(
        ApiResponse.success("Questionnaire answers saved successfully", response));
  }

  /**
   * Get current user's questionnaire answers.
   *
   * @return Map of question id to answer (string or list of strings)
   */
  @GetMapping("/questionnaire")
  public ResponseEntity<ApiResponse<QuestionnaireAnswersResponse>> getQuestionnaireAnswers() {
    String userId = userContext.requireCurrentUserId();
    logger.info("Fetching questionnaire answers for userId: {}", userId);

    var answers = userProfileService.getQuestionnaireAnswers(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            "Questionnaire answers retrieved successfully",
            new QuestionnaireAnswersResponse(answers)));
  }

  /**
   * Get one category with its questions and the user's answers for that category only. Use when
   * opening a category screen so the frontend can show questions and pre-fill existing answers.
   *
   * @param categoryId category id (e.g. "background", "interests")
   * @return category (with questions) and answers map for that category
   */
  @GetMapping("/questionnaire/category/{categoryId}")
  public ResponseEntity<ApiResponse<CategoryWithAnswersResponse>> getCategoryWithAnswers(
      @PathVariable String categoryId) {
    String userId = userContext.requireCurrentUserId();
    logger.info("Fetching category with answers for userId: {}, categoryId: {}", userId, categoryId);

    CategoryWithAnswersResponse response =
        userProfileService.getCategoryWithAnswers(userId, categoryId);
    return ResponseEntity.ok(
        ApiResponse.success(
            "Category and answers retrieved successfully",
            response));
  }

  /**
   * Get questionnaire progress: per-category completion percentage and total profile completion.
   *
   * @return categories (answeredCount, totalCount, percentage each), totalAnswered, totalQuestions,
   *     totalPercentage
   */
  @GetMapping("/questionnaire/progress")
  public ResponseEntity<ApiResponse<QuestionnaireProgressResponse>> getQuestionnaireProgress() {
    String userId = userContext.requireCurrentUserId();
    logger.info("Fetching questionnaire progress for userId: {}", userId);

    QuestionnaireProgressResponse progress = userProfileService.getQuestionnaireProgress(userId);
    return ResponseEntity.ok(
        ApiResponse.success("Questionnaire progress retrieved successfully", progress));
  }

  /**
   * Get user profile by userId.
   *
   * <p>This endpoint allows authenticated users to retrieve a profile by userId. Note: Consider
   * adding authorization checks to ensure users can only access appropriate profiles.
   *
   * @param userId The userId of the profile to retrieve
   * @return User profile response with profile data
   */
  @GetMapping("/profile/{userId}")
  public ResponseEntity<ApiResponse<UserProfileResponse>> getProfileById(
      @PathVariable String userId) {
    logger.info("Fetching profile for userId: {} (requested by authenticated user)", userId);

    UserProfileResponse response = userProfileService.getProfile(userId);

    return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", response));
  }

  /**
   * Update user profile and/or status by userId.
   *
   * <p>This unified PATCH endpoint handles both profile updates and soft delete operations. It can
   * process profile data updates, status changes, or both in a single request.
   *
   * @param userId The userId of the profile to update
   * @param request Unified update request containing profile data and/or status
   * @return Appropriate response based on the type of update
   */
  @PatchMapping("/profile/{userId}")
  public ResponseEntity<ApiResponse<?>> updateUserProfileById(
      @PathVariable String userId, @Valid @RequestBody UserProfileUpdateRequest request) {

    logger.info("Updating profile/status for userId: {} (requested by authenticated user)", userId);

    // Validate that the request contains at least one update
    if (!request.hasProfileUpdate() && !request.hasStatusUpdate()) {
      logger.warn("Empty update request for userId: {}", userId);
      ApiResponse<Object> errorResponse =
          ApiResponse.error("Request must contain at least one field to update");
      errorResponse.setErrorCode("EMPTY_REQUEST");
      return ResponseEntity.badRequest().body(errorResponse);
    }

    // Handle status update (soft delete/reactivate)
    if (request.hasStatusUpdate()) {
      logger.info(
          "Processing status update for userId: {} to status: {}", userId, request.getStatus());

      UserStatusUpdateRequest statusRequest =
          new UserStatusUpdateRequest(request.getStatus(), request.getReason());
      UserStatusUpdateResponse statusResponse =
          userProfileService.updateUserStatus(userId, statusRequest);

      // If only status update, return status response
      if (!request.hasProfileUpdate()) {
        String message =
            switch (request.getStatus()) {
              case DELETED -> "User profile deactivated successfully";
              case ACTIVE -> "User profile reactivated successfully";
            };
        return ResponseEntity.ok(ApiResponse.success(message, statusResponse));
      }
    }

    // Handle profile update (if user is active or being reactivated)
    if (request.hasProfileUpdate()) {
      logger.info("Processing profile update for userId: {}", userId);

      // Convert to UserProfileRequest
      UserProfileRequest profileRequest = convertToProfileRequest(request);
      UserProfileResponse profileResponse =
          userProfileService.createOrUpdateProfile(userId, profileRequest);

      // If both status and profile updated
      if (request.hasStatusUpdate()) {
        return ResponseEntity.ok(
            ApiResponse.success("User profile and status updated successfully", profileResponse));
      } else {
        return ResponseEntity.ok(
            ApiResponse.success("User profile updated successfully", profileResponse));
      }
    }

    // This shouldn't happen due to validation above
    ApiResponse<Object> errorResponse = ApiResponse.error("No valid updates found");
    errorResponse.setErrorCode("INVALID_REQUEST");
    return ResponseEntity.badRequest().body(errorResponse);
  }

  /**
   * Update user status (soft delete/reactivate).
   *
   * <p>This PATCH endpoint allows authenticated users to update user status for soft delete
   * operations. This follows the team's requirement to use PATCH API for user delete functionality.
   *
   * @param request User status update request
   * @return User status update response
   */
  @PatchMapping("/profile/status")
  public ResponseEntity<ApiResponse<UserStatusUpdateResponse>> updateCurrentUserStatus(
      @Valid @RequestBody UserStatusUpdateRequest request) {
    String userId = userContext.requireCurrentUserId();
    logger.info(
        "Updating status for current userId: {} to status: {}", userId, request.getStatus());

    UserStatusUpdateResponse response = userProfileService.updateUserStatus(userId, request);

    String message =
        switch (request.getStatus()) {
          case DELETED -> "User profile deactivated successfully";
          case ACTIVE -> "User profile reactivated successfully";
        };

    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  /**
   * Update user status by userId (admin operation).
   *
   * <p>This PATCH endpoint allows updating status for any user by userId. Consider adding proper
   * authorization checks for admin operations.
   *
   * @param userId The userId of the profile to update
   * @param request User status update request
   * @return User status update response
   */
  @PatchMapping("/profile/{userId}/status")
  public ResponseEntity<ApiResponse<UserStatusUpdateResponse>> updateUserStatusById(
      @PathVariable String userId, @Valid @RequestBody UserStatusUpdateRequest request) {
    logger.info(
        "Updating status for userId: {} to status: {} (requested by authenticated user)",
        userId,
        request.getStatus());

    UserStatusUpdateResponse response = userProfileService.updateUserStatus(userId, request);

    String message =
        switch (request.getStatus()) {
          case DELETED -> "User profile deactivated successfully";
          case ACTIVE -> "User profile reactivated successfully";
        };

    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  /**
   * Soft delete current user profile.
   *
   * <p>Convenience endpoint for soft deleting the current user's profile.
   *
   * @return User status update response
   */
  @PatchMapping("/profile/delete")
  public ResponseEntity<ApiResponse<UserStatusUpdateResponse>> softDeleteCurrentUser() {
    String userId = userContext.requireCurrentUserId();
    logger.info("Soft deleting current user profile for userId: {}", userId);

    UserStatusUpdateResponse response = userProfileService.softDeleteUser(userId);

    return ResponseEntity.ok(ApiResponse.success("User profile deleted successfully", response));
  }

  /**
   * Reactivate current user profile.
   *
   * <p>Convenience endpoint for reactivating the current user's profile.
   *
   * @return User status update response
   */
  @PatchMapping("/profile/reactivate")
  public ResponseEntity<ApiResponse<UserStatusUpdateResponse>> reactivateCurrentUser() {
    String userId = userContext.requireCurrentUserId();
    logger.info("Reactivating current user profile for userId: {}", userId);

    UserStatusUpdateResponse response = userProfileService.reactivateUser(userId);

    return ResponseEntity.ok(
        ApiResponse.success("User profile reactivated successfully", response));
  }

  /**
   * Convert UserProfileUpdateRequest to UserProfileRequest for service layer compatibility.
   *
   * @param updateRequest The unified update request
   * @return UserProfileRequest containing only profile fields
   */
  private UserProfileRequest convertToProfileRequest(UserProfileUpdateRequest updateRequest) {
    UserProfileRequest profileRequest = new UserProfileRequest();
    profileRequest.setDateOfBirth(updateRequest.getDateOfBirth());
    profileRequest.setAddress(updateRequest.getAddress());
    profileRequest.setCity(updateRequest.getCity());
    profileRequest.setState(updateRequest.getState());
    profileRequest.setZipCode(updateRequest.getZipCode());
    profileRequest.setCountry(updateRequest.getCountry());
    profileRequest.setGender(updateRequest.getGender());
    profileRequest.setProfession(updateRequest.getProfession());
    profileRequest.setCompany(updateRequest.getCompany());
    profileRequest.setBio(updateRequest.getBio());
    profileRequest.setPhoneNumber(updateRequest.getPhoneNumber());
    return profileRequest;
  }
}
