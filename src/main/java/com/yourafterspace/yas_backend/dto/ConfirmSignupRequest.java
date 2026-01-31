package com.yourafterspace.yas_backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmSignupRequest {

  @NotBlank(message = "Username is required")
  private String username;

  @NotBlank(message = "Confirmation code is required")
  private String confirmationCode;

  public ConfirmSignupRequest() {}

  public ConfirmSignupRequest(String username, String confirmationCode) {
    this.username = username;
    this.confirmationCode = confirmationCode;
  }

  // Getters and Setters
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getConfirmationCode() {
    return confirmationCode;
  }

  public void setConfirmationCode(String confirmationCode) {
    this.confirmationCode = confirmationCode;
  }
}
