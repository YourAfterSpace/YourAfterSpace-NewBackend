package com.yourafterspace.yas_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Response DTO for user profile data. */
public class UserProfileResponse {

  private String userId;

  @JsonFormat(pattern = "yyyy-MM-dd")
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
  /**
   * Questionnaire by category: each category has all questions; each question has an "answer"
   * field (user's value or null if not answered). Use this for all question/answer data; no separate
   * flat map is sent in the profile.
   */
  private List<CategoryWithQuestionsAndAnswersDto> questionnaireByCategory;
  /** Per-category completion percentage (e.g. background -> 80.0). */
  private Map<String, Double> categoryCompletionPercentages;
  /** Overall profile completion percentage (0–100). */
  private Double overallProfileCompletionPercentage;
  private UserStatus status;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
  private Instant createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
  private Instant updatedAt;

  public UserProfileResponse() {}

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

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

  public List<CategoryWithQuestionsAndAnswersDto> getQuestionnaireByCategory() {
    return questionnaireByCategory;
  }

  public void setQuestionnaireByCategory(List<CategoryWithQuestionsAndAnswersDto> questionnaireByCategory) {
    this.questionnaireByCategory = questionnaireByCategory;
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
}
