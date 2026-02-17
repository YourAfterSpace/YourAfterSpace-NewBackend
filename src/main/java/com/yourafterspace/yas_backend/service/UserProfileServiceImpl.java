package com.yourafterspace.yas_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourafterspace.yas_backend.dto.CategoryProgressDto;
import com.yourafterspace.yas_backend.dto.CategoryDto;
import com.yourafterspace.yas_backend.dto.CategoryWithAnswersResponse;
import com.yourafterspace.yas_backend.dto.CategoryWithQuestionsAndAnswersDto;
import com.yourafterspace.yas_backend.dto.QuestionDto;
import com.yourafterspace.yas_backend.dto.QuestionnaireProgressResponse;
import com.yourafterspace.yas_backend.dto.QuestionWithAnswerDto;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  private static final TypeReference<List<CategoryWithQuestionsAndAnswersDto>> QUESTIONNAIRE_BY_CATEGORY_TYPE =
      new TypeReference<>() {};

  private final UserProfileRepository userProfileRepository;
  private final QuestionService questionService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public UserProfileServiceImpl(
      UserProfileRepository userProfileRepository,
      QuestionService questionService) {
    this.userProfileRepository = userProfileRepository;
    this.questionService = questionService;
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
    response.setQuestionnaireByCategory(buildQuestionnaireByCategory(profile));
    response.setCategoryCompletionPercentages(profile.getCategoryCompletionPercentages());
    response.setOverallProfileCompletionPercentage(profile.getOverallProfileCompletionPercentage());
    response.setStatus(profile.getStatus());
    response.setCreatedAt(profile.getCreatedAt());
    response.setUpdatedAt(profile.getUpdatedAt());
    return response;
  }

  /**
   * Build questionnaire by category for response: always use the current template (all categories
   * and questions from questionService) and fill each question's answer from the user's stored
   * questionnaireAnswers map. This way new categories/questions added in code always appear in
   * GET profile; existing answers are preserved.
   */
  private List<CategoryWithQuestionsAndAnswersDto> buildQuestionnaireByCategory(UserProfile profile) {
    Map<String, Object> answers =
        profile.getQuestionnaireAnswers() != null ? profile.getQuestionnaireAnswers() : Collections.emptyMap();
    return buildQuestionnaireByCategoryFromTemplate(answers);
  }

  /** Build structure from template (categories + questions) with answer from map or null. */
  private List<CategoryWithQuestionsAndAnswersDto> buildQuestionnaireByCategoryFromTemplate(
      Map<String, Object> answers) {
    List<CategoryWithQuestionsAndAnswersDto> result = new ArrayList<>();
    for (CategoryDto cat : questionService.getCategories()) {
      CategoryWithQuestionsAndAnswersDto dto = new CategoryWithQuestionsAndAnswersDto();
      dto.setId(cat.getId());
      dto.setName(cat.getName());
      dto.setDescription(cat.getDescription());
      dto.setWeight(cat.getWeight());
      dto.setImageUrl(cat.getImageUrl());
      if (cat.getQuestions() != null) {
        List<QuestionWithAnswerDto> questionsWithAnswers = new ArrayList<>();
        for (QuestionDto q : cat.getQuestions()) {
          QuestionWithAnswerDto qwa = new QuestionWithAnswerDto();
          qwa.setId(q.getId());
          qwa.setTitle(q.getTitle());
          qwa.setDescription(q.getDescription());
          qwa.setType(q.getType());
          qwa.setOptions(q.getOptions());
          qwa.setCategoryId(q.getCategoryId());
          qwa.setCategoryName(q.getCategoryName());
          qwa.setWeight(q.getWeight());
          Object answer = answers.get(q.getId());
          qwa.setAnswer(answer);
          questionsWithAnswers.add(qwa);
        }
        dto.setQuestions(questionsWithAnswers);
      } else {
        dto.setQuestions(Collections.emptyList());
      }
      result.add(dto);
    }
    return result;
  }

  /** Derive flat map (questionId -> answer) from questionnaire-by-category structure. */
  private Map<String, Object> flatMapFromQuestionnaireByCategory(
      List<CategoryWithQuestionsAndAnswersDto> list) {
    Map<String, Object> flat = new HashMap<>();
    if (list == null) return flat;
    for (CategoryWithQuestionsAndAnswersDto cat : list) {
      if (cat.getQuestions() != null) {
        for (QuestionWithAnswerDto q : cat.getQuestions()) {
          if (q.getAnswer() != null) {
            flat.put(q.getId(), q.getAnswer());
          }
        }
      }
    }
    return flat;
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

  @Override
  public UserProfileResponse saveQuestionnaireAnswers(String userId, Map<String, Object> answers) {
    logger.info("Saving questionnaire answers for userId: {}", userId);

    UserProfile profile =
        userProfileRepository.findByUserId(userId).orElse(new UserProfile(userId));

    // Load current structure (stored JSON or build from template + existing answers)
    List<CategoryWithQuestionsAndAnswersDto> structure = buildQuestionnaireByCategory(profile);
    Map<String, Object> existingFlat = flatMapFromQuestionnaireByCategory(structure);
    if (answers != null && !answers.isEmpty()) {
      existingFlat.putAll(answers);
    }
    // Merge into structure: set each question's answer from merged flat map (or null)
    for (CategoryWithQuestionsAndAnswersDto cat : structure) {
      if (cat.getQuestions() != null) {
        for (QuestionWithAnswerDto q : cat.getQuestions()) {
          q.setAnswer(existingFlat.get(q.getId()));
        }
      }
    }

    try {
      profile.setQuestionnaireByCategoryJson(objectMapper.writeValueAsString(structure));
    } catch (Exception e) {
      throw new BadRequestException("Failed to serialize questionnaire", e);
    }
    profile.setQuestionnaireAnswers(flatMapFromQuestionnaireByCategory(structure));

    QuestionnaireProgressResponse progress = computeProgress(profile.getQuestionnaireAnswers());
    if (!progress.getCategories().isEmpty()) {
      Map<String, Double> categoryPct = new LinkedHashMap<>();
      for (CategoryProgressDto c : progress.getCategories()) {
        categoryPct.put(c.getCategoryId(), c.getPercentage());
      }
      profile.setCategoryCompletionPercentages(categoryPct);
    }
    profile.setOverallProfileCompletionPercentage(progress.getTotalPercentage());
    profile.setUpdatedAt(Instant.now());

    UserProfile saved = userProfileRepository.save(profile);

    logger.info("Questionnaire answers saved for userId: {}", userId);
    return toResponse(saved);
  }

  @Override
  public Map<String, Object> getQuestionnaireAnswers(String userId) {
    logger.info("Fetching questionnaire answers for userId: {}", userId);

    return userProfileRepository
        .findByUserId(userId)
        .map(UserProfile::getQuestionnaireAnswers)
        .filter(m -> m != null && !m.isEmpty())
        .orElse(Collections.emptyMap());
  }

  @Override
  public QuestionnaireProgressResponse getQuestionnaireProgress(String userId) {
    return computeProgress(getQuestionnaireAnswers(userId));
  }

  @Override
  public CategoryWithAnswersResponse getCategoryWithAnswers(String userId, String categoryId) {
    CategoryDto category =
        questionService
            .getCategoryById(categoryId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Category not found: " + categoryId));
    Map<String, Object> allAnswers = getQuestionnaireAnswers(userId);
    Map<String, Object> categoryAnswers = new HashMap<>();
    if (category.getQuestions() != null && !allAnswers.isEmpty()) {
      for (QuestionDto q : category.getQuestions()) {
        Object value = allAnswers.get(q.getId());
        if (value != null) {
          categoryAnswers.put(q.getId(), value);
        }
      }
    }
    return new CategoryWithAnswersResponse(category, categoryAnswers);
  }

  /**
   * Compute questionnaire progress from answers using per-question and per-category weightage.
   * Category % = (sum of weights of answered questions) / (sum of weights of all questions) * 100.
   * Overall % = sum(categoryWeight * categoryPercentage) / sum(categoryWeights) for categories with questions.
   */
  private QuestionnaireProgressResponse computeProgress(Map<String, Object> answers) {
    List<CategoryDto> categories = questionService.getCategories();

    List<CategoryProgressDto> categoryProgressList = new ArrayList<>();
    int totalAnswered = 0;
    int totalQuestions = 0;
    double weightedCategorySum = 0.0;   // sum of (categoryWeight * categoryPct)
    double totalCategoryWeight = 0.0;    // sum of category weights for categories that have questions

    for (CategoryDto cat : categories) {
      if (cat.getQuestions() == null || cat.getQuestions().isEmpty()) continue;

      double categoryWeight = cat.getWeight() != null ? cat.getWeight() : 1.0;
      double questionTotalWeight = 0.0;
      double questionAnsweredWeight = 0.0;
      int answeredCount = 0;

      for (QuestionDto q : cat.getQuestions()) {
        double w = q.getWeight() != null ? q.getWeight() : 1.0;
        questionTotalWeight += w;
        boolean answered = isAnswered(answers.get(q.getId()));
        if (answered) {
          questionAnsweredWeight += w;
          answeredCount++;
        }
      }

      int totalCount = cat.getQuestions().size();
      double pct =
          questionTotalWeight > 0
              ? (100.0 * questionAnsweredWeight / questionTotalWeight)
              : 0.0;

      categoryProgressList.add(
          new CategoryProgressDto(cat.getId(), cat.getName(), answeredCount, totalCount, pct));
      totalAnswered += answeredCount;
      totalQuestions += totalCount;

      totalCategoryWeight += categoryWeight;
      weightedCategorySum += categoryWeight * pct;
    }

    double totalPercentage =
        totalCategoryWeight > 0 ? (weightedCategorySum / totalCategoryWeight) : 0.0;

    return new QuestionnaireProgressResponse(
        categoryProgressList, totalAnswered, totalQuestions, totalPercentage);
  }

  private static boolean isAnswered(Object v) {
    if (v == null) return false;
    if (v instanceof String s) return !s.isBlank();
    if (v instanceof List<?> l) return !l.isEmpty();
    return true;
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
