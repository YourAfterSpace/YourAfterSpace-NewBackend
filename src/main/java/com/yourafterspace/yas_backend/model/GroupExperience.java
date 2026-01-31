package com.yourafterspace.yas_backend.model;

import java.time.Instant;

/**
 * GroupExperience entity stored in DynamoDB.
 *
 * <p>Represents the relationship between a Group and an Experience. PK: groupId, SK: experienceId
 * GSI1-PK: experienceId, GSI1-SK: groupId
 */
public class GroupExperience {

  private String groupId; // PK, GSI1-SK
  private String experienceId; // SK, GSI1-PK
  private Instant createdAt;
  private Instant updatedAt;

  public GroupExperience() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public GroupExperience(String groupId, String experienceId) {
    this();
    this.groupId = groupId;
    this.experienceId = experienceId;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getExperienceId() {
    return experienceId;
  }

  public void setExperienceId(String experienceId) {
    this.experienceId = experienceId;
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
}
