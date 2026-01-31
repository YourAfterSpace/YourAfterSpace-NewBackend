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
}
