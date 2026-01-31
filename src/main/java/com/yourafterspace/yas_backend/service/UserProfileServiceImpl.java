package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.UserProfileRequest;
import com.yourafterspace.yas_backend.dto.UserProfileResponse;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateRequest;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateResponse;
import com.yourafterspace.yas_backend.exception.BadRequestException;
import com.yourafterspace.yas_backend.exception.ResourceNotFoundException;
import com.yourafterspace.yas_backend.model.UserProfile;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import com.yourafterspace.yas_backend.repository.UserProfileRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of UserProfileService.
 *
 * <p>This service handles business logic for user profile operations, including validation and data
 * transformation.
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

  private static final Logger logger = LoggerFactory.getLogger(UserProfileServiceImpl.class);

  private final UserProfileRepository userProfileRepository;

  public UserProfileServiceImpl(UserProfileRepository userProfileRepository) {
    this.userProfileRepository = userProfileRepository;
  }

  @Override
  public UserProfileResponse createOrUpdateProfile(String userId, UserProfileRequest request) {
    logger.info("Creating or updating profile for userId: {}", userId);

    UserProfile profile =
        userProfileRepository.findByUserId(userId).orElse(new UserProfile(userId));

    // Update profile fields from request
    updateProfileFromRequest(profile, request);

    // Save to DynamoDB
    UserProfile savedProfile = userProfileRepository.save(profile);

    logger.info("Profile saved successfully for userId: {}", userId);
    return toResponse(savedProfile);
  }

  @Override
  public UserProfileResponse getProfile(String userId) {
    logger.info("Fetching profile for userId: {}", userId);

    UserProfile profile =
        userProfileRepository
            .findByUserId(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("User profile not found for userId: " + userId));

    logger.info("Profile found for userId: {}", userId);
    return toResponse(profile);
  }

  /**
   * Update UserProfile entity from request DTO.
   *
   * @param profile User profile entity to update
   * @param request Request DTO with new values
   */
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

    profile.setUpdatedAt(Instant.now());
  }

  /**
   * Convert UserProfile entity to response DTO.
   *
   * @param profile User profile entity
   * @return User profile response DTO
   */
  private UserProfileResponse toResponse(UserProfile profile) {
    UserProfileResponse response = new UserProfileResponse();
    response.setUserId(profile.getUserId());
    response.setDateOfBirth(profile.getDateOfBirth());
    response.setAddress(profile.getAddress());
    response.setCity(profile.getCity());
    response.setState(profile.getState());
    response.setZipCode(profile.getZipCode());
    response.setCountry(profile.getCountry());
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

  @Override
  public UserStatusUpdateResponse updateUserStatus(String userId, UserStatusUpdateRequest request) {
    logger.info("Updating status for userId: {} to status: {}", userId, request.getStatus());

    // Find the profile including deleted ones for status updates
    UserProfile profile =
        userProfileRepository
            .findByUserIdIncludingDeleted(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("User profile not found for userId: " + userId));

    UserStatus previousStatus = profile.getStatus();

    // Validate status transition if needed
    validateStatusTransition(previousStatus, request.getStatus());

    // Update status
    profile.setStatus(request.getStatus());
    profile.setUpdatedAt(Instant.now());

    // Save the updated profile
    userProfileRepository.save(profile);

    logger.info(
        "Status updated successfully for userId: {} from {} to {}",
        userId,
        previousStatus,
        request.getStatus());

    return new UserStatusUpdateResponse(
        userId, request.getStatus(), previousStatus, profile.getUpdatedAt());
  }

  @Override
  public UserStatusUpdateResponse softDeleteUser(String userId) {
    logger.info("Soft deleting user profile for userId: {}", userId);

    UserStatusUpdateRequest request =
        new UserStatusUpdateRequest(UserStatus.DELETED, "User soft delete");
    return updateUserStatus(userId, request);
  }

  @Override
  public UserStatusUpdateResponse reactivateUser(String userId) {
    logger.info("Reactivating user profile for userId: {}", userId);

    UserStatusUpdateRequest request =
        new UserStatusUpdateRequest(UserStatus.ACTIVE, "User reactivation");
    return updateUserStatus(userId, request);
  }

  /**
   * Validate status transition.
   *
   * @param currentStatus Current user status
   * @param newStatus New user status
   * @throws BadRequestException if transition is not allowed
   */
  private void validateStatusTransition(UserStatus currentStatus, UserStatus newStatus) {
    if (currentStatus == newStatus) {
      throw new BadRequestException("User is already in " + newStatus + " status");
    }

    // Add any business rules for status transitions here
    // For now, we allow all transitions between ACTIVE and DELETED
  }
}
