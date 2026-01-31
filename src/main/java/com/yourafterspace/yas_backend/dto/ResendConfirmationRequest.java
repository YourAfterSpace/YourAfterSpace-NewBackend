package com.yourafterspace.yas_backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ResendConfirmationRequest {

  @NotBlank(message = "Username is required")
  private String username;

  public ResendConfirmationRequest() {}

  public ResendConfirmationRequest(String username) {
    this.username = username;
  }

  // Getters and Setters
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
