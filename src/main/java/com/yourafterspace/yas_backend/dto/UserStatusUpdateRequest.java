package com.yourafterspace.yas_backend.dto;

import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import jakarta.validation.constraints.NotNull;

/** Request DTO for updating user status (soft delete/reactivate). */
public class UserStatusUpdateRequest {

  @NotNull(message = "Status is required")
  private UserStatus status;

  private String reason; // Optional reason for status change

  public UserStatusUpdateRequest() {}

  public UserStatusUpdateRequest(UserStatus status) {
    this.status = status;
  }

  public UserStatusUpdateRequest(UserStatus status, String reason) {
    this.status = status;
    this.reason = reason;
  }

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
}
