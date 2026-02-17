package com.yourafterspace.yas_backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * User profile entity stored in DynamoDB.
 *
 * <p>This model represents a user's profile information including personal details, address, and
 * professional information.
 */
public class UserProfile {

  private String userId; // Partition key - Cognito user ID
  private LocalDate dateOfBirth;
  private String address;
  private String city;
  private String state;
  private String zipCode;
  private String country;
  private Double latitude; // User's location latitude
  private Double longitude; // User's location longitude
  private String gender;
  private String profession;
  private String company;
  private String bio;
  private String phoneNumber;
  /** Questionnaire answers: key = question id, value = single string or list of strings */
  private Map<String, Object> questionnaireAnswers;
  /**
   * Questionnaire stored by category: each category has questions, each question has an "answer"
   * field (or null). JSON string of List of CategoryWithQuestionsAndAnswersDto.
   */
  private String questionnaireByCategoryJson;
  /** Per-category completion percentage (e.g. background -> 80.0). Stored in profile. */
  private Map<String, Double> categoryCompletionPercentages;
  /** Overall profile completion percentage (0–100). Stored in profile. */
  private Double overallProfileCompletionPercentage;
  private UserStatus status;
  /** Experience IDs the user has marked as interested (e.g. for wishlist). */
  private List<String> interestedExperienceIds;
  private Instant createdAt;
  private Instant updatedAt;

  public UserProfile() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.status = UserStatus.ACTIVE; // Default status
  }

  public UserProfile(String userId) {
    this();
    this.userId = userId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  @JsonFormat(pattern = "yyyy-MM-dd")
  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getZipCode() {
    return zipCode;
  }

  public void setZipCode(String zipCode) {
    this.zipCode = zipCode;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public String getProfession() {
    return profession;
  }

  public void setProfession(String profession) {
    this.profession = profession;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public Map<String, Object> getQuestionnaireAnswers() {
    return questionnaireAnswers;
  }

  public void setQuestionnaireAnswers(Map<String, Object> questionnaireAnswers) {
    this.questionnaireAnswers = questionnaireAnswers;
  }

  public String getQuestionnaireByCategoryJson() {
    return questionnaireByCategoryJson;
  }

  public void setQuestionnaireByCategoryJson(String questionnaireByCategoryJson) {
    this.questionnaireByCategoryJson = questionnaireByCategoryJson;
  }

  public Map<String, Double> getCategoryCompletionPercentages() {
    return categoryCompletionPercentages;
  }

  public void setCategoryCompletionPercentages(Map<String, Double> categoryCompletionPercentages) {
    this.categoryCompletionPercentages = categoryCompletionPercentages;
  }

  public Double getOverallProfileCompletionPercentage() {
    return overallProfileCompletionPercentage;
  }

  public void setOverallProfileCompletionPercentage(Double overallProfileCompletionPercentage) {
    this.overallProfileCompletionPercentage = overallProfileCompletionPercentage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public List<String> getInterestedExperienceIds() {
    return interestedExperienceIds;
  }

  public void setInterestedExperienceIds(List<String> interestedExperienceIds) {
    this.interestedExperienceIds = interestedExperienceIds;
  }

  /**
   * Check if the user profile is active (not deleted).
   *
   * @return true if status is ACTIVE, false otherwise
   */
  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  /**
   * Check if the user profile is deleted (soft deleted).
   *
   * @return true if status is DELETED, false otherwise
   */
  public boolean isDeleted() {
    return status == UserStatus.DELETED;
  }

  /** Soft delete this user profile. */
  public void delete() {
    this.status = UserStatus.DELETED;
    this.updatedAt = Instant.now();
  }

  /** Reactivate this user profile. */
  public void activate() {
    this.status = UserStatus.ACTIVE;
    this.updatedAt = Instant.now();
  }

  /** User status enumeration for soft delete functionality. */
  public enum UserStatus {
    ACTIVE("ACTIVE"),
    DELETED("DELETED");

    private final String value;

    UserStatus(String value) {
      this.value = value;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String getValue() {
      return value;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static UserStatus fromValue(String value) {
      if (value == null) {
        return ACTIVE;
      }
      for (UserStatus status : UserStatus.values()) {
        if (status.getValue().equalsIgnoreCase(value)) {
          return status;
        }
      }
      return ACTIVE; // Default fallback
    }
  }
}
