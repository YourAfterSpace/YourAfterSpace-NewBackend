package com.yourafterspace.yas_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourafterspace.yas_backend.model.Experience.ExperienceStatus;
import com.yourafterspace.yas_backend.model.Experience.ExperienceType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Request DTO for creating or updating experiences. */
public class ExperienceRequest {

  @NotBlank(message = "Title is required")
  @Size(max = 200, message = "Title must not exceed 200 characters")
  private String title;

  @Size(max = 2000, message = "Description must not exceed 2000 characters")
  private String description;

  @NotNull(message = "Experience type is required")
  private ExperienceType type;

  private ExperienceStatus status;

  // Location details
  @Size(max = 200, message = "Location name must not exceed 200 characters")
  private String location;

  @Size(max = 500, message = "Address must not exceed 500 characters")
  private String address;

  @Size(max = 100, message = "City must not exceed 100 characters")
  private String city;

  @Size(max = 100, message = "Country must not exceed 100 characters")
  private String country;

  @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
  @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
  private Double latitude;

  @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
  @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
  private Double longitude;

  // Timing details
  @Future(message = "Experience date must be in the future")
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate experienceDate;

  @JsonFormat(pattern = "HH:mm")
  private LocalTime startTime;

  @JsonFormat(pattern = "HH:mm")
  private LocalTime endTime;

  // Pricing and capacity
  @DecimalMin(value = "0.0", message = "Price must be non-negative")
  @Digits(integer = 10, fraction = 2, message = "Price format is invalid")
  private BigDecimal pricePerPerson;

  @Size(max = 3, message = "Currency code must be 3 characters")
  private String currency;

  @Min(value = 1, message = "Maximum capacity must be at least 1")
  @Max(value = 10000, message = "Maximum capacity cannot exceed 10000")
  private Integer maxCapacity;

  // Additional details
  @Size(max = 20, message = "Cannot have more than 20 tags")
  private List<@Size(max = 50, message = "Each tag must not exceed 50 characters") String> tags;

  @Size(max = 10, message = "Cannot have more than 10 images")
  private List<
          @Pattern(regexp = "^https?://.*", message = "Image URLs must be valid HTTP/HTTPS URLs")
          String>
      images;

  @Size(max = 500, message = "Contact info must not exceed 500 characters")
  private String contactInfo;

  @Size(max = 1000, message = "Requirements must not exceed 1000 characters")
  private String requirements;

  @Size(max = 1000, message = "Cancellation policy must not exceed 1000 characters")
  private String cancellationPolicy;

  public ExperienceRequest() {}

  // Getters and Setters
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
}
