package com.yourafterspace.yas_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourafterspace.yas_backend.model.Experience.ExperienceStatus;
import com.yourafterspace.yas_backend.model.Experience.ExperienceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Response DTO for experience data. */
public class ExperienceResponse {

  private String experienceId;
  private String createdBy;
  private String title;
  private String description;
  private ExperienceType type;
  private ExperienceStatus status;

  // Location details
  private String location;
  private String address;
  private String city;
  private String country;
  private Double latitude;
  private Double longitude;

  // Timing details
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate experienceDate;

  @JsonFormat(pattern = "HH:mm")
  private LocalTime startTime;

  @JsonFormat(pattern = "HH:mm")
  private LocalTime endTime;

  // Pricing and capacity
  private BigDecimal pricePerPerson;
  private String currency;
  private Integer maxCapacity;
  private Integer currentBookings;
  private Integer remainingCapacity;

  // Additional details
  private List<String> tags;
  private List<String> images;
  private String contactInfo;
  private String requirements;
  private String cancellationPolicy;

  // Ratings and reviews
  private Double averageRating;
  private Integer totalReviews;

  // Availability
  private Boolean hasAvailableSpots;

  // Timestamps
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
  private Instant createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
  private Instant updatedAt;

  public ExperienceResponse() {}

  // Getters and Setters
  public String getExperienceId() {
    return experienceId;
  }

  public void setExperienceId(String experienceId) {
    this.experienceId = experienceId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ExperienceType getType() {
    return type;
  }

  public void setType(ExperienceType type) {
    this.type = type;
  }

  public ExperienceStatus getStatus() {
    return status;
  }

  public void setStatus(ExperienceStatus status) {
    this.status = status;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
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

  public LocalDate getExperienceDate() {
    return experienceDate;
  }

  public void setExperienceDate(LocalDate experienceDate) {
    this.experienceDate = experienceDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalTime endTime) {
    this.endTime = endTime;
  }

  public BigDecimal getPricePerPerson() {
    return pricePerPerson;
  }

  public void setPricePerPerson(BigDecimal pricePerPerson) {
    this.pricePerPerson = pricePerPerson;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getMaxCapacity() {
    return maxCapacity;
  }

  public void setMaxCapacity(Integer maxCapacity) {
    this.maxCapacity = maxCapacity;
  }

  public Integer getCurrentBookings() {
    return currentBookings;
  }

  public void setCurrentBookings(Integer currentBookings) {
    this.currentBookings = currentBookings;
  }

  public Integer getRemainingCapacity() {
    return remainingCapacity;
  }

  public void setRemainingCapacity(Integer remainingCapacity) {
    this.remainingCapacity = remainingCapacity;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public List<String> getImages() {
    return images;
  }

  public void setImages(List<String> images) {
    this.images = images;
  }

  public String getContactInfo() {
    return contactInfo;
  }

  public void setContactInfo(String contactInfo) {
    this.contactInfo = contactInfo;
  }

  public String getRequirements() {
    return requirements;
  }

  public void setRequirements(String requirements) {
    this.requirements = requirements;
  }

  public String getCancellationPolicy() {
    return cancellationPolicy;
  }

  public void setCancellationPolicy(String cancellationPolicy) {
    this.cancellationPolicy = cancellationPolicy;
  }

  public Double getAverageRating() {
    return averageRating;
  }

  public void setAverageRating(Double averageRating) {
    this.averageRating = averageRating;
  }

  public Integer getTotalReviews() {
    return totalReviews;
  }

  public void setTotalReviews(Integer totalReviews) {
    this.totalReviews = totalReviews;
  }

  public Boolean getHasAvailableSpots() {
    return hasAvailableSpots;
  }

  public void setHasAvailableSpots(Boolean hasAvailableSpots) {
    this.hasAvailableSpots = hasAvailableSpots;
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
}
