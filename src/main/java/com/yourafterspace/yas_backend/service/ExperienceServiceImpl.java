package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dao.ExperienceDao;
import com.yourafterspace.yas_backend.dao.UserExperienceDao;
import com.yourafterspace.yas_backend.dto.ExperienceRequest;
import com.yourafterspace.yas_backend.dto.ExperienceResponse;
import com.yourafterspace.yas_backend.exception.AuthenticationException;
import com.yourafterspace.yas_backend.exception.BadRequestException;
import com.yourafterspace.yas_backend.exception.ResourceNotFoundException;
import com.yourafterspace.yas_backend.model.Experience;
import com.yourafterspace.yas_backend.model.Experience.ExperienceStatus;
import com.yourafterspace.yas_backend.model.UserExperience;
import com.yourafterspace.yas_backend.model.UserExperience.UserExperienceStatus;
import com.yourafterspace.yas_backend.repository.ExperienceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/** Implementation of ExperienceService for managing experiences. */
@Service
public class ExperienceServiceImpl implements ExperienceService {

  private static final Logger logger = LoggerFactory.getLogger(ExperienceServiceImpl.class);

  private final ExperienceRepository experienceRepository;
  private final ExperienceDao experienceDao;
  private final UserExperienceDao userExperienceDao;

  public ExperienceServiceImpl(
      ExperienceRepository experienceRepository,
      ExperienceDao experienceDao,
      UserExperienceDao userExperienceDao) {
    this.experienceRepository = experienceRepository;
    this.experienceDao = experienceDao;
    this.userExperienceDao = userExperienceDao;
  }

  @Override
  public ExperienceResponse createExperience(ExperienceRequest request, String createdBy) {
    logger.debug("Creating new experience for user: {}", createdBy);

    validateExperienceRequest(request, true);

    Experience experience = new Experience(createdBy);
    mapRequestToExperience(request, experience);

    Experience savedExperience = experienceRepository.save(experience);
    logger.info("Created experience {} for user {}", savedExperience.getExperienceId(), createdBy);

    return mapExperienceToResponse(savedExperience);
  }

  @Override
  public ExperienceResponse getExperience(String experienceId) {
    logger.debug("Getting experience: {}", experienceId);

    Experience experience = findExperienceById(experienceId);
    return mapExperienceToResponse(experience);
  }

  @Override
  public ExperienceResponse updateExperience(
      String experienceId, ExperienceRequest request, String userId) {
    logger.debug("Updating experience {} by user {}", experienceId, userId);

    Experience existingExperience = findExperienceById(experienceId);

    // Check if user can edit this experience
    if (!userId.equals(existingExperience.getCreatedBy())) {
      throw new AuthenticationException("You don't have permission to edit this experience");
    }

    validateExperienceRequest(request, false);

    // Update fields from request
    mapRequestToExperience(request, existingExperience);

    Experience updatedExperience = experienceRepository.save(existingExperience);
    logger.info("Updated experience {} by user {}", experienceId, userId);

    return mapExperienceToResponse(updatedExperience);
  }

  /** Find experience by ID or throw exception if not found. */
  private Experience findExperienceById(String experienceId) {
    return experienceRepository
        .findById(experienceId)
        .orElseThrow(() -> new ResourceNotFoundException("Experience not found: " + experienceId));
  }

  /** Validate experience request. */
  private void validateExperienceRequest(ExperienceRequest request, boolean isCreate) {
    // Validate date logic
    if (request.getExperienceDate() != null
        && request.getExperienceDate().isBefore(LocalDate.now())) {
      throw new BadRequestException("Experience date must be in the future");
    }

    // Validate time logic
    if (request.getStartTime() != null && request.getEndTime() != null) {
      if (request.getStartTime().isAfter(request.getEndTime())) {
        throw new BadRequestException("Start time must be before end time");
      }
    }

    // Validate capacity
    if (request.getMaxCapacity() != null && request.getMaxCapacity() <= 0) {
      throw new BadRequestException("Maximum capacity must be positive");
    }

    // Validate price
    if (request.getPricePerPerson() != null
        && request.getPricePerPerson().compareTo(java.math.BigDecimal.ZERO) < 0) {
      throw new BadRequestException("Price cannot be negative");
    }
  }

  /** Map ExperienceRequest to Experience entity. */
  private void mapRequestToExperience(ExperienceRequest request, Experience experience) {
    // Copy properties that exist in both
    BeanUtils.copyProperties(request, experience, "createdBy", "experienceId");

    // Set default status if not provided
    if (request.getStatus() == null) {
      experience.setStatus(ExperienceStatus.DRAFT);
    }

    // Set default currency if not provided
    if (request.getCurrency() == null) {
      experience.setCurrency("USD");
    }
  }

  @Override
  public List<ExperienceResponse> getPastAttendedExperiences(String userId) {
    logger.debug("Getting past attended experiences for user: {}", userId);

    // Get all user experiences with ATTENDED status
    List<UserExperience> userExperiences =
        userExperienceDao.findByUserIdAndStatus(userId, UserExperienceStatus.ATTENDED);

    List<ExperienceResponse> pastExperiences = new ArrayList<>();
    Instant now = Instant.now();
    ZoneId utc = ZoneId.of("UTC");

    for (UserExperience userExperience : userExperiences) {
      String experienceId = userExperience.getExperienceId();
      if (experienceId == null || experienceId.isBlank()) {
        logger.warn(
            "UserExperience record found with null/blank experienceId for userId={}, status=ATTENDED",
            userId);
        continue;
      }

      // Get experience details
      var experienceOpt = experienceDao.findByExperienceId(experienceId);
      if (experienceOpt.isEmpty()) {
        logger.debug("Experience not found for experienceId: {}", experienceId);
        continue;
      }

      Experience experience = experienceOpt.get();

      // Check if experience is in the past
      if (experience.getExperienceDate() != null) {
        boolean isPast = false;

        if (experience.getStartTime() != null) {
          // Both date and time provided - combine them
          LocalDateTime experienceStartDateTime =
              experience.getExperienceDate().atTime(experience.getStartTime());
          Instant experienceStartInstant = experienceStartDateTime.atZone(utc).toInstant();

          // Include if experience start time < current time (experience is in the past)
          if (experienceStartInstant.isBefore(now)) {
            isPast = true;
          }
        } else {
          // Only date provided, no time - check if date < today
          LocalDate today = LocalDate.now(utc);
          if (experience.getExperienceDate().isBefore(today)) {
            isPast = true;
          }
        }

        if (isPast) {
          pastExperiences.add(mapExperienceToResponse(experience));
        }
      } else {
        // No date, can't determine if past - skip
        logger.debug("Skipping experience {} - no experienceDate", experienceId);
      }
    }

    logger.info("Found {} past attended experiences for user {}", pastExperiences.size(), userId);
    return pastExperiences;
  }

  /** Map Experience entity to ExperienceResponse DTO. */
  private ExperienceResponse mapExperienceToResponse(Experience experience) {
    ExperienceResponse response = new ExperienceResponse();
    BeanUtils.copyProperties(experience, response);

    // Calculate derived fields
    response.setRemainingCapacity(experience.getRemainingCapacity());
    response.setHasAvailableSpots(experience.hasAvailableSpots());

    return response;
  }

  @Override
  public List<ExperienceResponse> getUpcomingPaidExperiences(String userId) {
    logger.debug("Getting upcoming paid experiences for user: {}", userId);

    // Get all user experiences with PAID status
    List<UserExperience> userExperiences =
        userExperienceDao.findByUserIdAndStatus(userId, UserExperienceStatus.PAID);

    List<ExperienceResponse> upcomingExperiences = new ArrayList<>();
    Instant now = Instant.now();
    ZoneId utc = ZoneId.of("UTC");

    for (UserExperience userExperience : userExperiences) {
      String experienceId = userExperience.getExperienceId();
      if (experienceId == null || experienceId.isBlank()) {
        logger.warn(
            "UserExperience record found with null/blank experienceId for userId={}, status=PAID",
            userId);
        continue;
      }

      // Get experience details
      var experienceOpt = experienceDao.findByExperienceId(experienceId);
      if (experienceOpt.isEmpty()) {
        logger.debug("Experience not found for experienceId: {}", experienceId);
        continue;
      }

      Experience experience = experienceOpt.get();

      // Check if experience is in the future
      if (experience.getExperienceDate() != null) {
        boolean isUpcoming = false;

        if (experience.getStartTime() != null) {
          // Both date and time provided - combine them
          LocalDateTime experienceStartDateTime =
              experience.getExperienceDate().atTime(experience.getStartTime());
          Instant experienceStartInstant = experienceStartDateTime.atZone(utc).toInstant();

          // Include if experience start time > current time (experience is in the future)
          if (experienceStartInstant.isAfter(now)) {
            isUpcoming = true;
          }
        } else {
          // Only date provided, no time - check if date >= today
          LocalDate today = LocalDate.now(utc);
          if (experience.getExperienceDate().isAfter(today)
              || experience.getExperienceDate().isEqual(today)) {
            // If today, assume upcoming (or valid for today)
            isUpcoming = true;
          }
        }

        if (isUpcoming) {
          upcomingExperiences.add(mapExperienceToResponse(experience));
        }
      } else {
        // No date, can't determine if upcoming - skip
        logger.debug("Skipping experience {} - no experienceDate", experienceId);
      }
    }

    logger.info(
        "Found {} upcoming paid experiences for user {}", upcomingExperiences.size(), userId);
    return upcomingExperiences;
  }
}
