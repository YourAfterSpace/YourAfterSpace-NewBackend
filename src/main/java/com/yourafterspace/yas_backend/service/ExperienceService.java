package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.ExperienceRequest;
import com.yourafterspace.yas_backend.dto.ExperienceResponse;
import java.util.List;

/** Service interface for experience management. */
public interface ExperienceService {

  /**
   * Create a new experience.
   *
   * @param request Experience creation request
   * @param createdBy User ID who is creating the experience
   * @return Created experience response
   */
  ExperienceResponse createExperience(ExperienceRequest request, String createdBy);

  /**
   * Get an experience by ID.
   *
   * @param experienceId Experience ID
   * @return Experience response
   */
  ExperienceResponse getExperience(String experienceId);

  /**
   * Update an existing experience (including soft delete via status).
   *
   * @param experienceId Experience ID
   * @param request Update request
   * @param userId User ID making the request (must be creator)
   * @return Updated experience response
   */
  ExperienceResponse updateExperience(
      String experienceId, ExperienceRequest request, String userId);

  /**
   * Get all past experiences that a user has attended.
   *
   * @param userId User ID
   * @return List of past attended experiences
   */
  List<ExperienceResponse> getPastAttendedExperiences(String userId);

  /**
   * Get all upcoming experiences that a user has paid for.
   *
   * @param userId User ID
   * @return List of upcoming paid experiences
   */
  List<ExperienceResponse> getUpcomingPaidExperiences(String userId);

  /**
   * Get all experiences in the database (catalog listing).
   *
   * @return List of all experiences
   */
  List<ExperienceResponse> getAllExperiences();

  /**
   * Get all experiences in a given city.
   *
   * @param city City name (e.g. "Mumbai") – case-sensitive match
   * @return List of experiences in that city
   */
  List<ExperienceResponse> getExperiencesByCity(String city);

  /**
   * Mark or unmark the current user as interested in an experience (stored on user profile).
   *
   * @param userId Current user ID
   * @param experienceId Experience ID
   * @param interested true to add to interested list, false to remove
   */
  void markInterested(String userId, String experienceId, boolean interested);

  /**
   * Get all experiences the user has marked as interested.
   *
   * @param userId Current user ID
   * @return List of interested experiences (full details)
   */
  List<ExperienceResponse> getInterestedExperiences(String userId);
}
