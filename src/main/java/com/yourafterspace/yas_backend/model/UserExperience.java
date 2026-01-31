package com.yourafterspace.yas_backend.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * UserExperience entity stored in DynamoDB.
 *
 * <p>Represents the relationship between a User and an Experience with additional metadata. PK:
 * userId, SK: experienceId GSI1-PK: experienceId, GSI1-SK: userId
 */
public class UserExperience {

  private String userId; // PK, GSI1-SK
  private String experienceId; // SK, GSI1-PK
  private Instant experienceTime; // When the user experienced this
  private Double interestScore; // User's interest level (0.0 to 1.0)
  private Boolean
      expInterest; // User's interest in the experience (true/false) - stored as "exp-interest" in
  // DynamoDB
  private PaymentDetails paymentDetails;
  private UserExperienceStatus status;
  private Boolean paid; // User has paid for the experience (true/false) - stored as "PAID" in
  // DynamoDB
  private Instant createdAt;
  private Instant updatedAt;

  public UserExperience() {
    this.status = UserExperienceStatus.INTERESTED;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UserExperience(String userId, String experienceId) {
    this();
    this.userId = userId;
    this.experienceId = experienceId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getExperienceId() {
    return experienceId;
  }

  public void setExperienceId(String experienceId) {
    this.experienceId = experienceId;
  }

  public Instant getExperienceTime() {
    return experienceTime;
  }

  public void setExperienceTime(Instant experienceTime) {
    this.experienceTime = experienceTime;
  }

  public Double getInterestScore() {
    return interestScore;
  }

  public void setInterestScore(Double interestScore) {
    this.interestScore = interestScore;
  }

  public Boolean getExpInterest() {
    return expInterest;
  }

  public void setExpInterest(Boolean expInterest) {
    this.expInterest = expInterest;
  }

  public PaymentDetails getPaymentDetails() {
    return paymentDetails;
  }

  public void setPaymentDetails(PaymentDetails paymentDetails) {
    this.paymentDetails = paymentDetails;
  }

  public UserExperienceStatus getStatus() {
    return status;
  }

  public void setStatus(UserExperienceStatus status) {
    this.status = status;
    // Automatically set paid field based on status
    if (status != null) {
      if (status == UserExperienceStatus.PAID || status == UserExperienceStatus.ATTENDED) {
        // PAID and ATTENDED both imply paid
        this.paid = true;
      } else {
        this.paid = false;
      }
    }
  }

  public Boolean getPaid() {
    return paid;
  }

  public void setPaid(Boolean paid) {
    this.paid = paid;
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

  /** User experience status enumeration. */
  public enum UserExperienceStatus {
    INTERESTED("INTERESTED"),
    PAID("PAID"),
    ATTENDED("ATTENDED"),
    CANCELLED("CANCELLED");

    private final String value;

    UserExperienceStatus(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static UserExperienceStatus fromValue(String value) {
      if (value == null) {
        return INTERESTED;
      }
      for (UserExperienceStatus status : UserExperienceStatus.values()) {
        if (status.getValue().equalsIgnoreCase(value)) {
          return status;
        }
      }
      return INTERESTED;
    }
  }

  /** Payment details nested class. */
  public static class PaymentDetails {
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private Instant paymentDate;
    private String transactionId;

    public PaymentDetails() {}

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public String getCurrency() {
      return currency;
    }

    public void setCurrency(String currency) {
      this.currency = currency;
    }

    public String getPaymentMethod() {
      return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
      this.paymentMethod = paymentMethod;
    }

    public Instant getPaymentDate() {
      return paymentDate;
    }

    public void setPaymentDate(Instant paymentDate) {
      this.paymentDate = paymentDate;
    }

    public String getTransactionId() {
      return transactionId;
    }

    public void setTransactionId(String transactionId) {
      this.transactionId = transactionId;
    }
  }
}
