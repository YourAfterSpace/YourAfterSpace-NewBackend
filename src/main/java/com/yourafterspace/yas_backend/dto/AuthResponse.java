package com.yourafterspace.yas_backend.dto;

public class AuthResponse {
  private String accessToken;
  private String idToken;
  private String refreshToken;
  private String tokenType;
  private Integer expiresIn;
  private String userId;
  private String username;

  public AuthResponse() {
    this.tokenType = "Bearer";
  }

  public AuthResponse(
      String accessToken,
      String idToken,
      String refreshToken,
      Integer expiresIn,
      String userId,
      String username) {
    this.accessToken = accessToken;
    this.idToken = idToken;
    this.refreshToken = refreshToken;
    this.tokenType = "Bearer";
    this.expiresIn = expiresIn;
    this.userId = userId;
    this.username = username;
  }

  // Getters and Setters
  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public String getIdToken() {
    return idToken;
  }

  public void setIdToken(String idToken) {
    this.idToken = idToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  public Integer getExpiresIn() {
    return expiresIn;
  }

  public void setExpiresIn(Integer expiresIn) {
    this.expiresIn = expiresIn;
  }

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
}
