package com.yourafterspace.yas_backend.controller;

import com.yourafterspace.yas_backend.dto.ApiResponse;
import com.yourafterspace.yas_backend.dto.UserProfileRequest;
import com.yourafterspace.yas_backend.dto.UserProfileResponse;
import com.yourafterspace.yas_backend.service.UserProfileService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for user endpoints.
 *
 * <p>This controller handles user-related operations. The userId is passed via the UserId header in
 * requests.
 */
@RestController
@RequestMapping("/v1/users")
public class UserController {

  private static final Logger logger = LoggerFactory.getLogger(UserController.class);

  private final UserProfileService userProfileService;

  public UserController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  /**
   * Create or update user profile.
   *
   * <p>This endpoint allows authenticated users to create or update their profile information. If
   * the profile does not exist, it will be created. If it exists, it will be updated.
   *
   * @param userId User ID passed in the UserId header
   * @param request User profile request containing profile data
   * @return User profile response with saved data
   */
  @PutMapping("/profile")
  public ResponseEntity<ApiResponse<UserProfileResponse>> createOrUpdateProfile(
      @RequestHeader("UserId") String userId, @Valid @RequestBody UserProfileRequest request) {
    logger.info("Creating or updating profile for userId: {}", userId);

    UserProfileResponse response = userProfileService.createOrUpdateProfile(userId, request);

    return ResponseEntity.ok(ApiResponse.success("User profile saved successfully", response));
  }
}
