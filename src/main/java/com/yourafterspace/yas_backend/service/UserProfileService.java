package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.UserProfileRequest;
import com.yourafterspace.yas_backend.dto.UserProfileResponse;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateRequest;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateResponse;

/**
 * Interface for user profile operations.
 *
 * <p>This interface defines the contract for user profile services, allowing for different
 * implementations if needed.
 */
public interface UserProfileService {

  /**
   * Create or update a user profile.
   *
   * @param userId Cognito user ID
   * @param request User profile request data
   * @return User profile response
   * @throws com.yourafterspace.yas_backend.exception.BadRequestException if validation fails
   */
  UserProfileResponse createOrUpdateProfile(String userId, UserProfileRequest request);

  /**
   * Get user profile by userId.
   *
   * @param userId Cognito user ID
   * @return User profile response
   * @throws com.yourafterspace.yas_backend.exception.ResourceNotFoundException if profile not found
   */
  UserProfileResponse getProfile(String userId);

  /**
   * Update user status (soft delete/reactivate).
   *
   * @param userId Cognito user ID
   * @param request User status update request
   * @return User status update response
   * @throws com.yourafterspace.yas_backend.exception.ResourceNotFoundException if profile not found
   * @throws com.yourafterspace.yas_backend.exception.BadRequestException if status change is
   *     invalid
   */
  UserStatusUpdateResponse updateUserStatus(String userId, UserStatusUpdateRequest request);

  /**
   * Soft delete a user profile.
   *
   * @param userId Cognito user ID
   * @return User status update response
   * @throws com.yourafterspace.yas_backend.exception.ResourceNotFoundException if profile not found
   */
  UserStatusUpdateResponse softDeleteUser(String userId);

  /**
   * Reactivate a soft-deleted user profile.
   *
   * @param userId Cognito user ID
   * @return User status update response
   * @throws com.yourafterspace.yas_backend.exception.ResourceNotFoundException if profile not found
   */
  UserStatusUpdateResponse reactivateUser(String userId);
}
