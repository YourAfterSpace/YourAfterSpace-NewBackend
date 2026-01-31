package com.yourafterspace.yas_backend.dao;

import com.yourafterspace.yas_backend.model.Group;
import com.yourafterspace.yas_backend.model.Group.GroupStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Data Access Object (DAO) for Group entity operations in DynamoDB.
 *
 * <p>Why DAO? - Separation of concerns: Business logic separated from data access - Reusability:
 * Can be used in Lambda handlers, Spring services, etc. - Testability: Easy to mock for unit
 * testing - Maintainability: Changes to DynamoDB schema only affect this class
 *
 * <p>Table structure: PK: GROUP#{group_id}, SK: UserId GSI1-PK: UserId, GSI1-SK: groupId
 */
public class GroupDao {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public GroupDao(DynamoDbClient dynamoDbClient, String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Save a group. Creates a new group or updates existing one. Uses UpdateItem for updates to
   * preserve existing attributes not in the Group model.
   *
   * @param group Group to save
   * @return Saved group
   */
  public Group save(Group group) {
    group.setUpdatedAt(Instant.now());
    if (group.getCreatedAt() == null) {
      group.setCreatedAt(Instant.now());
    }

    // Normalize groupId
    String normalizedGroupId =
        group.getGroupId().startsWith("GROUP#")
            ? group.getGroupId()
            : "GROUP#" + group.getGroupId();

    // Check if group exists
    Optional<Group> existing = findByGroupId(normalizedGroupId);

    if (existing.isPresent()) {
      // Use UpdateItem to preserve existing attributes (like experienceId if present)
      updateGroup(group, existing.get());
    } else {
      // Use PutItem for new groups
      Map<String, AttributeValue> item = toAttributeMap(group);
      PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();
      dynamoDbClient.putItem(putRequest);
    }

    return group;
  }

  /**
   * Update an existing group using UpdateItem to preserve attributes not in the Group model.
   *
   * @param group Group with updated values
   * @param existing Existing group from database
   */
  private void updateGroup(Group group, Group existing) {
    // Build key
    String normalizedGroupId =
        group.getGroupId().startsWith("GROUP#")
            ? group.getGroupId()
            : "GROUP#" + group.getGroupId();

    Map<String, AttributeValue> key = new HashMap<>();
    key.put("pk", AttributeValue.builder().s(normalizedGroupId).build());
    key.put("sk", AttributeValue.builder().s(existing.getCreatedAt().toString()).build());

    // Build update expression
    // Use expression attribute names for reserved keywords (like "status")
    Map<String, String> expressionAttributeNames = new HashMap<>();
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    List<String> updateParts = new ArrayList<>();

    // Update memberUserIds
    List<String> memberUserIds = group.getMemberUserIds();
    if (memberUserIds == null) {
      memberUserIds = new ArrayList<>();
    }
    if (memberUserIds.isEmpty()) {
      updateParts.add("memberUserIds = :memberUserIds");
      expressionAttributeValues.put(
          ":memberUserIds", AttributeValue.builder().ss(new String[0]).build());
    } else {
      updateParts.add("memberUserIds = :memberUserIds");
      expressionAttributeValues.put(
          ":memberUserIds",
          AttributeValue.builder().ss(memberUserIds.toArray(new String[0])).build());
    }

    // Update groupName if provided
    if (group.getGroupName() != null) {
      updateParts.add("groupName = :groupName");
      expressionAttributeValues.put(
          ":groupName", AttributeValue.builder().s(group.getGroupName()).build());
    }

    // Update description if provided
    if (group.getDescription() != null) {
      updateParts.add("description = :description");
      expressionAttributeValues.put(
          ":description", AttributeValue.builder().s(group.getDescription()).build());
    }

    // Update status if provided - use expression attribute name since "status" is a reserved
    // keyword
    if (group.getStatus() != null) {
      expressionAttributeNames.put("#status", "status");
      updateParts.add("#status = :status");
      expressionAttributeValues.put(
          ":status", AttributeValue.builder().s(group.getStatus().getValue()).build());
    }

    // Always update updatedAt
    updateParts.add("updatedAt = :updatedAt");
    expressionAttributeValues.put(
        ":updatedAt", AttributeValue.builder().s(group.getUpdatedAt().toString()).build());

    // Update GSI5 attributes if userId is present
    if (group.getUserId() != null && !group.getUserId().isBlank()) {
      updateParts.add("GSI5PK = :gsi5pk");
      updateParts.add("GSI5SK = :gsi5sk");
      expressionAttributeValues.put(
          ":gsi5pk", AttributeValue.builder().s(group.getUserId()).build());
      expressionAttributeValues.put(
          ":gsi5sk", AttributeValue.builder().s(normalizedGroupId).build());
    }

    // Build update expression
    String updateExpression = "SET " + String.join(", ", updateParts);

    // Update item (this preserves attributes not mentioned in the update expression)
    UpdateItemRequest.Builder updateRequestBuilder =
        UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpression)
            .expressionAttributeValues(expressionAttributeValues);

    // Only add expressionAttributeNames if we have reserved keywords
    if (!expressionAttributeNames.isEmpty()) {
      updateRequestBuilder.expressionAttributeNames(expressionAttributeNames);
    }

    UpdateItemRequest updateRequest = updateRequestBuilder.build();

    dynamoDbClient.updateItem(updateRequest);
  }

  /**
   * Find a group by groupId. Returns the first group found (groups can have multiple users).
   *
   * <p>Note: The table uses "userId" as the partition key attribute name, and groups are stored
   * with the groupId (with GROUP# prefix) as the partition key value.
   *
   * @param groupId Group ID (with or without GROUP# prefix)
   * @return Optional containing the group if found
   */
  public Optional<Group> findByGroupId(String groupId) {
    String normalizedGroupId = groupId.startsWith("GROUP#") ? groupId : "GROUP#" + groupId;

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":pk", AttributeValue.builder().s(normalizedGroupId).build());
    // Add prefix for begins_with on sort key (createdAt) - ISO 8601 timestamps start with year
    // Using "2" to match years 2000-2999 (covers most modern dates)
    expressionAttributeValues.put(":skPrefix", AttributeValue.builder().s("2").build());

    // Query using pk as partition key (the table's PK attribute name)
    // The groupId value is stored in the pk partition key field
    // Table structure: PK = pk, SK = sk
    // For composite keys, DynamoDB requires both PK and SK conditions
    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)") // pk is PK, sk is SK
            .expressionAttributeValues(expressionAttributeValues)
            .scanIndexForward(false) // Get latest first
            .limit(1)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    // Debug: Log all items found
    System.out.println(
        "DEBUG: GroupDao.findByGroupId - Found "
            + response.items().size()
            + " items for groupId: "
            + normalizedGroupId);
    if (!response.items().isEmpty()) {
      for (int i = 0; i < response.items().size(); i++) {
        Map<String, AttributeValue> item = response.items().get(i);
        String sk = item.containsKey("sk") ? item.get("sk").s() : "null";
        boolean hasMemberUserIds = item.containsKey("memberUserIds");
        System.out.println(
            "DEBUG: Item " + i + " - sk: " + sk + ", has memberUserIds: " + hasMemberUserIds);
      }
    }

    // If no results with "2" prefix (years 2000+), try "1" prefix (years 1000-1999)
    if (response.items().isEmpty()) {
      expressionAttributeValues.put(":skPrefix", AttributeValue.builder().s("1").build());
      queryRequest =
          QueryRequest.builder()
              .tableName(tableName)
              .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)")
              .expressionAttributeValues(expressionAttributeValues)
              .scanIndexForward(false)
              .limit(1)
              .build();
      response = dynamoDbClient.query(queryRequest);

      // Debug: Log items from fallback query
      System.out.println(
          "DEBUG: GroupDao.findByGroupId - Fallback query found "
              + response.items().size()
              + " items");
      if (!response.items().isEmpty()) {
        for (int i = 0; i < response.items().size(); i++) {
          Map<String, AttributeValue> item = response.items().get(i);
          String sk = item.containsKey("sk") ? item.get("sk").s() : "null";
          boolean hasMemberUserIds = item.containsKey("memberUserIds");
          System.out.println(
              "DEBUG: Fallback Item "
                  + i
                  + " - sk: "
                  + sk
                  + ", has memberUserIds: "
                  + hasMemberUserIds);
        }
      }
    }

    if (response.items().isEmpty()) {
      return Optional.empty();
    }

    // Return the first item (should be the latest due to scanIndexForward(false))
    Map<String, AttributeValue> selectedItem = response.items().get(0);
    System.out.println(
        "DEBUG: GroupDao.findByGroupId - Returning item with sk: "
            + (selectedItem.containsKey("sk") ? selectedItem.get("sk").s() : "null"));
    return Optional.of(fromAttributeMap(selectedItem));
  }

  /**
   * Find all groups for a specific user.
   *
   * <p>Note: Since groups are stored with userId = "GROUP#groupId" (not the actual user ID), we
   * cannot use GSI1 to query by user ID. Instead, we need to scan and filter by memberUserIds
   * containing the user ID. This is less efficient but necessary given the table structure.
   *
   * <p>Alternatively, if GSI1 exists with a different structure, we could use it. But for now,
   * we'll use a scan with filter expression.
   *
   * @param userId User ID
   * @return List of groups the user belongs to
   */
  public List<Group> findByUserId(String userId) {
    // Since groups are stored with userId = "GROUP#groupId", we can't query GSI1 by actual user ID
    // Instead, we need to scan and filter by memberUserIds containing the user
    // This is less efficient but works with the current table structure

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":userId", AttributeValue.builder().s(userId).build());

    // Try GSI5 first (for user-group queries)
    // GSI5: GSI5PK = userId, GSI5SK = GROUP#{groupId}
    try {
      QueryRequest queryRequest =
          QueryRequest.builder()
              .tableName(tableName)
              .indexName("GSI5") // UserId-GroupId index
              .keyConditionExpression("GSI5PK = :userId")
              .expressionAttributeValues(expressionAttributeValues)
              .build();

      QueryResponse response = dynamoDbClient.query(queryRequest);

      List<Group> groups = new ArrayList<>();
      for (Map<String, AttributeValue> item : response.items()) {
        // Filter to only include Group items (check for groupId attribute)
        if (item.containsKey("groupId") && item.get("groupId").s().startsWith("GROUP#")) {
          groups.add(fromAttributeMap(item));
        }
      }

      if (!groups.isEmpty()) {
        System.out.println(
            "DEBUG: GSI5 query found " + groups.size() + " groups for user: " + userId);
        return groups;
      }
    } catch (Exception e) {
      // GSI5 doesn't exist or uses different structure, fall back to scan
      System.out.println("DEBUG: GSI5 query failed, falling back to scan: " + e.getMessage());
    }

    // Fallback: Scan table and filter by memberUserIds containing the user
    // This is less efficient but works
    ScanRequest scanRequest =
        ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("contains(memberUserIds, :userId)")
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

    List<Group> groups = new ArrayList<>();
    for (Map<String, AttributeValue> item : scanResponse.items()) {
      // Filter to only include Group items (check for groupId attribute)
      if (item.containsKey("groupId") && item.get("groupId").s().startsWith("GROUP#")) {
        groups.add(fromAttributeMap(item));
      }
    }

    return groups;
  }

  /**
   * Check if a group exists.
   *
   * @param groupId Group ID
   * @return true if group exists, false otherwise
   */
  public boolean exists(String groupId) {
    return findByGroupId(groupId).isPresent();
  }

  /**
   * Delete a group permanently from DynamoDB (hard delete).
   *
   * @param group Group to delete
   */
  public void delete(Group group) {
    String normalizedGroupId =
        group.getGroupId().startsWith("GROUP#")
            ? group.getGroupId()
            : "GROUP#" + group.getGroupId();

    // Build the composite key (PK: pk = groupId, SK: sk)
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("pk", AttributeValue.builder().s(normalizedGroupId).build());
    if (group.getCreatedAt() != null) {
      key.put("sk", AttributeValue.builder().s(group.getCreatedAt().toString()).build());
    } else {
      // If sk is missing, we can't delete (need both PK and SK)
      throw new IllegalArgumentException(
          "Cannot delete group: sk (sort key) is required for deletion");
    }

    DeleteItemRequest deleteRequest =
        DeleteItemRequest.builder().tableName(tableName).key(key).build();
    dynamoDbClient.deleteItem(deleteRequest);
  }

  /** Convert Group to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(Group group) {
    Map<String, AttributeValue> item = new HashMap<>();

    // PK: pk attribute = groupId value (e.g., "GROUP#my-group-001")
    // This allows querying by groupId using the pk partition key
    String normalizedGroupId =
        group.getGroupId().startsWith("GROUP#")
            ? group.getGroupId()
            : "GROUP#" + group.getGroupId();
    item.put("pk", AttributeValue.builder().s(normalizedGroupId).build());

    // Store groupId as regular attribute for reference
    item.put("groupId", AttributeValue.builder().s(normalizedGroupId).build());
    // Store the actual user ID (creator) as a regular attribute
    if (group.getUserId() != null) {
      item.put("creatorUserId", AttributeValue.builder().s(group.getUserId()).build());
    }

    if (group.getGroupName() != null) {
      item.put("groupName", AttributeValue.builder().s(group.getGroupName()).build());
    }
    if (group.getDescription() != null) {
      item.put("description", AttributeValue.builder().s(group.getDescription()).build());
    }
    if (group.getStatus() != null) {
      item.put("status", AttributeValue.builder().s(group.getStatus().getValue()).build());
    }
    // Always save memberUserIds, even if empty (to ensure updates work correctly)
    // Initialize as empty list if null to ensure attribute always exists in DynamoDB
    List<String> memberUserIds = group.getMemberUserIds();
    if (memberUserIds == null) {
      memberUserIds = new ArrayList<>();
    }
    if (memberUserIds.isEmpty()) {
      // Save empty list as empty string set to ensure attribute exists
      item.put("memberUserIds", AttributeValue.builder().ss(new String[0]).build());
    } else {
      item.put(
          "memberUserIds",
          AttributeValue.builder().ss(memberUserIds.toArray(new String[0])).build());
    }
    // sk is required as it's the sort key - save method ensures it's never null
    if (group.getCreatedAt() != null) {
      item.put("sk", AttributeValue.builder().s(group.getCreatedAt().toString()).build());
    } else {
      // Fallback: set sk if somehow null (shouldn't happen due to save method)
      item.put("sk", AttributeValue.builder().s(Instant.now().toString()).build());
    }
    if (group.getUpdatedAt() != null) {
      item.put("updatedAt", AttributeValue.builder().s(group.getUpdatedAt().toString()).build());
    }

    // GSI5 attributes: GSI5PK = userId (member), GSI5SK = GROUP#{groupId}
    // This allows querying groups by member userId
    // Create GSI5 entries for each member in memberUserIds
    if (memberUserIds != null && !memberUserIds.isEmpty()) {
      // Note: DynamoDB doesn't support multiple items with same PK but different SK in a single
      // item
      // For now, we'll store the first member's userId in GSI5PK for the group item
      // For full member-based queries, we may need a separate relationship table or use scan
      // This is a limitation of single-table design - we'll use scan for member queries
      // But we can still set GSI5 for the creator
      if (group.getUserId() != null && !group.getUserId().isBlank()) {
        item.put("GSI5PK", AttributeValue.builder().s(group.getUserId()).build());
        item.put("GSI5SK", AttributeValue.builder().s(normalizedGroupId).build());
      }
    }

    return item;
  }

  /** Convert DynamoDB AttributeValue map to Group. */
  private Group fromAttributeMap(Map<String, AttributeValue> item) {
    // Debug: Log all attribute names in the item
    System.out.println("DEBUG: GroupDao.fromAttributeMap - Item keys: " + item.keySet());
    System.out.println(
        "DEBUG: GroupDao.fromAttributeMap - Has memberUserIds: "
            + item.containsKey("memberUserIds"));
    if (item.containsKey("memberUserIds")) {
      AttributeValue memberUserIdsAttr = item.get("memberUserIds");
      System.out.println(
          "DEBUG: GroupDao.fromAttributeMap - memberUserIds attribute type: "
              + (memberUserIdsAttr.ss() != null
                  ? "String Set (SS)"
                  : memberUserIdsAttr.s() != null
                      ? "String (S)"
                      : memberUserIdsAttr.l() != null ? "List (L)" : "Unknown"));
    }

    Group group = new Group();

    // PK (pk attribute) contains the groupId value
    if (item.containsKey("pk")) {
      String pkValue = item.get("pk").s();
      // If it starts with GROUP#, it's a groupId stored in PK
      if (pkValue.startsWith("GROUP#")) {
        group.setGroupId(pkValue);
      } else {
        // Legacy: if pk doesn't start with GROUP#, treat it as user ID (creator)
        group.setUserId(pkValue);
        // Also set as groupId for legacy compatibility (without GROUP# prefix)
        group.setGroupId("GROUP#" + pkValue);
      }
    }
    // Also check groupId attribute (for consistency)
    if (item.containsKey("groupId")) {
      group.setGroupId(item.get("groupId").s());
    }
    // Get creator user ID from creatorUserId attribute (if exists) - this takes precedence
    if (item.containsKey("creatorUserId")) {
      group.setUserId(item.get("creatorUserId").s());
    } else if (item.containsKey("pk") && !item.get("pk").s().startsWith("GROUP#")) {
      // Fallback: if pk doesn't start with GROUP#, it's the creator user ID (legacy)
      group.setUserId(item.get("pk").s());
    }
    // If pk starts with GROUP# but no creatorUserId exists, group.getUserId() will be null
    // This is a data integrity issue - groups should always have creatorUserId
    if (item.containsKey("groupName")) {
      group.setGroupName(item.get("groupName").s());
    }
    if (item.containsKey("description")) {
      group.setDescription(item.get("description").s());
    }
    if (item.containsKey("status")) {
      group.setStatus(GroupStatus.fromValue(item.get("status").s()));
    }
    if (item.containsKey("memberUserIds")) {
      try {
        AttributeValue memberUserIdsAttr = item.get("memberUserIds");
        if (memberUserIdsAttr != null) {
          // Check if it's a String Set (SS)
          if (memberUserIdsAttr.ss() != null) {
            List<String> memberIds = memberUserIdsAttr.ss();
            if (memberIds != null && !memberIds.isEmpty()) {
              group.setMemberUserIds(new ArrayList<>(memberIds));
            } else {
              // Empty string set - initialize as empty list
              group.setMemberUserIds(new ArrayList<>());
            }
          } else {
            // Not a String Set - try as String (S) or List (L)
            System.out.println(
                "DEBUG: memberUserIds is not a String Set, type: " + memberUserIdsAttr);
            group.setMemberUserIds(new ArrayList<>());
          }
        } else {
          group.setMemberUserIds(new ArrayList<>());
        }
      } catch (Exception e) {
        System.out.println("DEBUG: Error reading memberUserIds: " + e.getMessage());
        e.printStackTrace();
        group.setMemberUserIds(new ArrayList<>());
      }
    } else {
      // memberUserIds attribute doesn't exist - initialize as empty list
      // This handles legacy groups created before memberUserIds was added
      System.out.println(
          "DEBUG: memberUserIds attribute not found in DynamoDB item for group: "
              + group.getGroupId());
      group.setMemberUserIds(new ArrayList<>());
    }
    if (item.containsKey("sk")) {
      group.setCreatedAt(Instant.parse(item.get("sk").s()));
    }
    if (item.containsKey("updatedAt")) {
      group.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return group;
  }
}
