package com.yourafterspace.yas_backend.model;

import java.time.Instant;
import java.util.List;

/**
 * Group entity stored in DynamoDB.
 *
 * <p>This model represents a group of users. PK: GROUP#{group_id}, SK: UserId GSI1-PK: UserId,
 * GSI1-SK: groupId
 */
public class Group {

  private String groupId; // PK: GROUP#{group_id}
  private String userId; // SK, GSI1-PK
  private String groupName;
  private String description;
  private GroupStatus status;
  private List<String> memberUserIds; // List of user IDs in the group
  private Instant createdAt;
  private Instant updatedAt;

  public Group() {
    this.status = GroupStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public Group(String groupId, String userId) {
    this();
    this.groupId = "GROUP#" + groupId;
    this.userId = userId;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public GroupStatus getStatus() {
    return status;
  }

  public void setStatus(GroupStatus status) {
    this.status = status;
  }

  public List<String> getMemberUserIds() {
    return memberUserIds;
  }

  public void setMemberUserIds(List<String> memberUserIds) {
    this.memberUserIds = memberUserIds;
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

  /** Group status enumeration. */
  public enum GroupStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    DELETED("DELETED");

    private final String value;

    GroupStatus(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static GroupStatus fromValue(String value) {
      if (value == null) {
        return ACTIVE;
      }
      for (GroupStatus status : GroupStatus.values()) {
        if (status.getValue().equalsIgnoreCase(value)) {
          return status;
        }
      }
      return ACTIVE;
    }
  }
}
