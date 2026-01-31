package com.yourafterspace.yas_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignupRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Email must be valid")
  private String email;

  @NotBlank(message = "Password is required")
  @Size(min = 8, message = "Password must be at least 8 characters long")
  // Note: Cognito also requires uppercase, lowercase, number, and special character
  private String password;

  // Note: Username field is kept for backward compatibility, but if your Cognito
  // User Pool requires email as username, the email will be used instead.
  // This field can be used for display purposes or if your pool allows custom usernames.
  private String username;

  private String firstName;
  private String lastName;
  private String phoneNumber;

  public SignupRequest() {}

  public SignupRequest(String email, String password, String username) {
    this.email = email;
    this.password = password;
    this.username = username;
  }

  // Getters and Setters
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }
}
