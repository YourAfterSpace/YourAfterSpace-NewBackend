package com.yourafterspace.yas_backend.dto;

public class SignupResponse {
  private String userId;
  private String username;
  private String email;
  private boolean confirmed;
  private String message;

  public SignupResponse() {}

  public SignupResponse(
      String userId, String username, String email, boolean confirmed, String message) {
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.confirmed = confirmed;
    this.message = message;
  }

  // Getters and Setters
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public boolean isConfirmed() {
    return confirmed;
  }

  public void setConfirmed(boolean confirmed) {
    this.confirmed = confirmed;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
