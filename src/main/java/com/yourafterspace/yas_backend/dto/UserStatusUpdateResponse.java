package com.yourafterspace.yas_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import java.time.Instant;

/** Response DTO for user status update operations. */
public class UserStatusUpdateResponse {

  private String userId;
  private UserStatus status;
  private UserStatus previousStatus;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
  private Instant updatedAt;

  public UserStatusUpdateResponse() {}

  public UserStatusUpdateResponse(
      String userId, UserStatus status, UserStatus previousStatus, Instant updatedAt) {
    this.userId = userId;
    this.status = status;
    this.previousStatus = previousStatus;
    this.updatedAt = updatedAt;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public UserStatus getPreviousStatus() {
    return previousStatus;
  }

  public void setPreviousStatus(UserStatus previousStatus) {
    this.previousStatus = previousStatus;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
