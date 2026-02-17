package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.CategoryWithAnswersResponse;
import com.yourafterspace.yas_backend.dto.QuestionnaireProgressResponse;
import com.yourafterspace.yas_backend.dto.UserProfileRequest;
import com.yourafterspace.yas_backend.dto.UserProfileResponse;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateRequest;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateResponse;
import java.util.Map;

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

  /**
   * Save questionnaire answers for the user. Merges with existing profile or creates one.
   *
   * @param userId Cognito user ID
   * @param answers Map of question id to answer (String or List of String)
   * @return Updated profile response including questionnaire answers
   */
  UserProfileResponse saveQuestionnaireAnswers(String userId, Map<String, Object> answers);

  /**
   * Get questionnaire answers for the user.
   *
   * @param userId Cognito user ID
   * @return Map of question id to answer, or empty map if none saved
   */
  Map<String, Object> getQuestionnaireAnswers(String userId);

  /**
   * Get questionnaire progress: per-category percentage and total profile completion.
   *
   * @param userId Cognito user ID
   * @return Progress per category and total percentage
   */
  QuestionnaireProgressResponse getQuestionnaireProgress(String userId);

  /**
   * Get one category with its questions and the user's answers for that category only. Use when
   * opening a category screen so frontend can show questions and pre-fill existing answers.
   *
   * @param userId Cognito user ID
   * @param categoryId category id (e.g. "background", "interests")
   * @return Category with questions and answers map (question id → value) for that category only
   * @throws com.yourafterspace.yas_backend.exception.ResourceNotFoundException if category not found
   */
  CategoryWithAnswersResponse getCategoryWithAnswers(String userId, String categoryId);
}
