package com.yourafterspace.yas_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Unified request DTO for updating user profile and/or status. This DTO supports both profile
 * updates and soft delete operations.
 */
public class UserProfileUpdateRequest {

  // Profile fields (from original UserProfileRequest)
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate dateOfBirth;

  @Size(max = 500, message = "Address must not exceed 500 characters")
  private String address;

  @Size(max = 100, message = "City must not exceed 100 characters")
  private String city;

  @Size(max = 100, message = "State must not exceed 100 characters")
  private String state;

  @Size(max = 20, message = "Zip code must not exceed 20 characters")
  private String zipCode;

  @Size(max = 100, message = "Country must not exceed 100 characters")
  private String country;

  @Size(max = 50, message = "Gender must not exceed 50 characters")
  private String gender;

  @Size(max = 100, message = "Profession must not exceed 100 characters")
  private String profession;

  @Size(max = 200, message = "Company must not exceed 200 characters")
  private String company;

  @Size(max = 1000, message = "Bio must not exceed 1000 characters")
  private String bio;

  @Size(max = 20, message = "Phone number must not exceed 20 characters")
  private String phoneNumber;

  // Status fields (from UserStatusUpdateRequest)
  private UserStatus status;

  @Size(max = 500, message = "Reason must not exceed 500 characters")
  private String reason;

  public UserProfileUpdateRequest() {}

  // Profile field getters and setters
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

  // Status field getters and setters
  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  /** Check if this request contains status update fields. */
  public boolean hasStatusUpdate() {
    return status != null;
  }

  /** Check if this request contains profile update fields. */
  public boolean hasProfileUpdate() {
    return dateOfBirth != null
        || address != null
        || city != null
        || state != null
        || zipCode != null
        || country != null
        || gender != null
        || profession != null
        || company != null
        || bio != null
        || phoneNumber != null;
  }
}
