package com.yourafterspace.yas_backend.dao;

import com.yourafterspace.yas_backend.model.GroupExperience;
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

/**
 * Data Access Object (DAO) for GroupExperience entity operations in DynamoDB.
 *
 * <p>Why DAO? - Handles the many-to-many relationship between Groups and Experiences - Provides
 * clean query methods for both directions (group->experiences, experience->groups) - Reusable
 * across different contexts
 *
 * <p>Table structure: PK: userId (GROUP#{groupId}), SK: createdAt GSI2-PK:
 * EXPERIENCE#{experienceId}, GSI2-SK: GROUP#{groupId}
 */
public class GroupExperienceDao {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public GroupExperienceDao(DynamoDbClient dynamoDbClient, String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Save a group-experience relationship.
   *
   * @param groupExperience GroupExperience to save
   * @return Saved GroupExperience
   */
  public GroupExperience save(GroupExperience groupExperience) {
    groupExperience.setUpdatedAt(Instant.now());
    if (groupExperience.getCreatedAt() == null) {
      groupExperience.setCreatedAt(Instant.now());
    }

    Map<String, AttributeValue> item = toAttributeMap(groupExperience);
    PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();
    dynamoDbClient.putItem(putRequest);

    return groupExperience;
  }

  /**
   * Find all experiences for a group.
   *
   * <p>Note: Since GroupExperience is stored in the same table (YourAfterSpace) with PK: userId,
   * SK: createdAt, we need to use userId as the partition key attribute name. The groupId value is
   * stored in the userId attribute (with GROUP# prefix).
   *
   * @param groupId Group ID
   * @return List of GroupExperience relationships
   */
  public List<GroupExperience> findByGroupId(String groupId) {
    // Normalize groupId (add GROUP# prefix if not present)
    String normalizedGroupId = groupId.startsWith("GROUP#") ? groupId : "GROUP#" + groupId;

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":pk", AttributeValue.builder().s(normalizedGroupId).build());
    // Add prefix for begins_with on sort key (createdAt) - using "2" to match years 2000+
    expressionAttributeValues.put(":skPrefix", AttributeValue.builder().s("2").build());

    // Query using pk as partition key (the table's PK attribute name)
    // Table structure: PK: pk, SK: sk
    // For composite keys, DynamoDB requires both PK and SK conditions
    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)") // pk is PK, sk is SK
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    // If no results with "2" prefix (years 2000+), try "1" prefix (years 1000-1999)
    if (response.items().isEmpty()) {
      expressionAttributeValues.put(":skPrefix", AttributeValue.builder().s("1").build());
      queryRequest =
          QueryRequest.builder()
              .tableName(tableName)
              .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)")
              .expressionAttributeValues(expressionAttributeValues)
              .build();
      response = dynamoDbClient.query(queryRequest);
    }

    List<GroupExperience> groupExperiences = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      // Filter to only include GroupExperience items (check for experienceId attribute)
      if (item.containsKey("experienceId") && item.containsKey("groupId")) {
        groupExperiences.add(fromAttributeMap(item));
      }
    }

    return groupExperiences;
  }

  /**
   * Find all groups for an experience (using GSI1).
   *
   * <p>Note: Since GroupExperience is stored in the same table, we need to query using the table's
   * structure. The experienceId is stored as a regular attribute, not as a key. We'll need to scan
   * or use a filter expression. However, if GSI1 exists with experienceId as PK, we can use it.
   *
   * @param experienceId Experience ID (can be with or without EXPERIENCE# prefix)
   * @return List of GroupExperience relationships
   */
  public List<GroupExperience> findByExperienceId(String experienceId) {
    // Normalize experienceId - ensure it has EXPERIENCE# prefix for GSI1PK
    // GSI1 uses GSI1PK as partition key, which should contain the full experienceId with prefix
    String normalizedExperienceId =
        experienceId.startsWith("EXPERIENCE#") ? experienceId : "EXPERIENCE#" + experienceId;

    System.out.println(
        "DEBUG: GroupExperienceDao.findByExperienceId - querying for experienceId: "
            + experienceId
            + " (normalized for GSI1PK: "
            + normalizedExperienceId
            + ")");

    // Try GSI2 query first (for experience-group relationships)
    // GSI2: GSI2PK = EXPERIENCE#{experienceId}, GSI2SK = GROUP#{groupId}
    try {
      String gsi2PK =
          normalizedExperienceId.startsWith("EXPERIENCE#")
              ? normalizedExperienceId
              : "EXPERIENCE#" + normalizedExperienceId;

      Map<String, AttributeValue> gsi2Values = new HashMap<>();
      gsi2Values.put(":expId", AttributeValue.builder().s(gsi2PK).build());

      QueryRequest gsi2Request =
          QueryRequest.builder()
              .tableName(tableName)
              .indexName("GSI2")
              .keyConditionExpression("GSI2PK = :expId")
              .expressionAttributeValues(gsi2Values)
              .build();

      QueryResponse gsi2Response = dynamoDbClient.query(gsi2Request);

      if (!gsi2Response.items().isEmpty()) {
        List<GroupExperience> groupExperiences = new ArrayList<>();
        for (Map<String, AttributeValue> item : gsi2Response.items()) {
          if (item.containsKey("experienceId") && item.containsKey("groupId")) {
            groupExperiences.add(fromAttributeMap(item));
          }
        }
        System.out.println(
            "DEBUG: GSI2 query found "
                + groupExperiences.size()
                + " GroupExperience relationships");
        return groupExperiences;
      }
    } catch (Exception e) {
      System.out.println("DEBUG: GSI2 query failed, falling back to scan: " + e.getMessage());
    }

    // Fallback to scan if GSI2 doesn't exist or query fails
    System.out.println("DEBUG: Using scan method (GSI2 may not be available)");

    try {
      Map<String, AttributeValue> scanExpressionValues = new HashMap<>();
      // Try multiple variations: with EXPERIENCE# prefix, without prefix, and original
      scanExpressionValues.put(
          ":expId1", AttributeValue.builder().s(normalizedExperienceId).build());
      scanExpressionValues.put(":expId2", AttributeValue.builder().s(experienceId).build());
      // Also try without EXPERIENCE# prefix (in case it was stored without prefix)
      String withoutPrefix =
          experienceId.startsWith("EXPERIENCE#")
              ? experienceId.replace("EXPERIENCE#", "")
              : experienceId;
      scanExpressionValues.put(":expId3", AttributeValue.builder().s(withoutPrefix).build());

      ScanRequest scanRequest =
          ScanRequest.builder()
              .tableName(tableName)
              .filterExpression(
                  "attribute_exists(experienceId) AND attribute_exists(groupId) AND (experienceId = :expId1 OR experienceId = :expId2 OR experienceId = :expId3)")
              .expressionAttributeValues(scanExpressionValues)
              .build();

      ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
      System.out.println(
          "DEBUG: Scan found " + scanResponse.items().size() + " items with experienceId filter");
      System.out.println(
          "DEBUG: Searching for experienceId variations: "
              + normalizedExperienceId
              + ", "
              + experienceId
              + ", "
              + withoutPrefix);

      // Process scan results
      List<GroupExperience> groupExperiences = new ArrayList<>();
      for (Map<String, AttributeValue> item : scanResponse.items()) {
        if (item.containsKey("experienceId") && item.containsKey("groupId")) {
          String itemExperienceId = item.get("experienceId").s();
          String itemGroupId = item.get("groupId").s();
          System.out.println(
              "DEBUG: Found GroupExperience - experienceId: "
                  + itemExperienceId
                  + ", groupId: "
                  + itemGroupId);
          groupExperiences.add(fromAttributeMap(item));
        } else {
          System.out.println(
              "DEBUG: Item missing experienceId or groupId - keys: " + item.keySet());
        }
      }
      System.out.println(
          "DEBUG: GroupExperienceDao.findByExperienceId - returning "
              + groupExperiences.size()
              + " relationships");
      return groupExperiences;
    } catch (Throwable scanException) {
      System.out.println(
          "DEBUG: Scan failed: "
              + scanException.getClass().getSimpleName()
              + " - "
              + scanException.getMessage());
      scanException.printStackTrace();
      // Return empty list if scan fails
      return new ArrayList<>();
    }
  }

  /**
   * Delete a group-experience relationship.
   *
   * <p>Note: Since GroupExperience is stored in the same table with PK: userId, SK: createdAt, we
   * need to find the item first to get its createdAt, then delete using the composite key.
   *
   * @param groupId Group ID
   * @param experienceId Experience ID
   */
  public void delete(String groupId, String experienceId) {
    // Normalize IDs
    String normalizedGroupId = groupId.startsWith("GROUP#") ? groupId : "GROUP#" + groupId;

    // Find the relationship to get its createdAt (sort key)
    List<GroupExperience> relationships = findByGroupId(normalizedGroupId);
    Optional<GroupExperience> toDelete =
        relationships.stream().filter(ge -> ge.getExperienceId().equals(experienceId)).findFirst();

    if (toDelete.isEmpty()) {
      throw new IllegalArgumentException(
          "GroupExperience relationship not found: groupId="
              + normalizedGroupId
              + ", experienceId="
              + experienceId);
    }

    GroupExperience ge = toDelete.get();
    if (ge.getCreatedAt() == null) {
      throw new IllegalArgumentException(
          "Cannot delete GroupExperience: createdAt (sort key) is required for deletion");
    }

    // Build composite key (PK: pk, SK: sk)
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("pk", AttributeValue.builder().s(normalizedGroupId).build());
    key.put("sk", AttributeValue.builder().s(ge.getCreatedAt().toString()).build());

    DeleteItemRequest deleteRequest =
        DeleteItemRequest.builder().tableName(tableName).key(key).build();
    dynamoDbClient.deleteItem(deleteRequest);
  }

  /** Convert GroupExperience to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(GroupExperience groupExperience) {
    Map<String, AttributeValue> item = new HashMap<>();

    // PK: pk attribute = groupId value (e.g., "GROUP#my-group-001")
    // This allows querying by groupId using the pk partition key
    String normalizedGroupId =
        groupExperience.getGroupId().startsWith("GROUP#")
            ? groupExperience.getGroupId()
            : "GROUP#" + groupExperience.getGroupId();
    item.put("pk", AttributeValue.builder().s(normalizedGroupId).build());

    // SK: sk attribute (required for composite key)
    if (groupExperience.getCreatedAt() != null) {
      item.put("sk", AttributeValue.builder().s(groupExperience.getCreatedAt().toString()).build());
    } else {
      // Fallback: set sk if somehow null
      item.put("sk", AttributeValue.builder().s(Instant.now().toString()).build());
    }

    // Store groupId and experienceId as regular attributes for reference
    item.put("groupId", AttributeValue.builder().s(normalizedGroupId).build());
    item.put("experienceId", AttributeValue.builder().s(groupExperience.getExperienceId()).build());

    // GSI2 attributes: GSI2PK = EXPERIENCE#{experienceId}, GSI2SK = GROUP#{groupId}
    // These are required for querying GSI2 by experienceId
    String normalizedExperienceId =
        groupExperience.getExperienceId().startsWith("EXPERIENCE#")
            ? groupExperience.getExperienceId()
            : "EXPERIENCE#" + groupExperience.getExperienceId();
    item.put("GSI2PK", AttributeValue.builder().s(normalizedExperienceId).build());
    item.put("GSI2SK", AttributeValue.builder().s(normalizedGroupId).build());

    if (groupExperience.getUpdatedAt() != null) {
      item.put(
          "updatedAt",
          AttributeValue.builder().s(groupExperience.getUpdatedAt().toString()).build());
    }

    return item;
  }

  /** Convert DynamoDB AttributeValue map to GroupExperience. */
  private GroupExperience fromAttributeMap(Map<String, AttributeValue> item) {
    GroupExperience groupExperience = new GroupExperience();

    // PK (pk attribute) contains the groupId value
    if (item.containsKey("pk")) {
      String pkValue = item.get("pk").s();
      // If it starts with GROUP#, it's a groupId stored in PK
      if (pkValue.startsWith("GROUP#")) {
        groupExperience.setGroupId(pkValue);
      }
    }
    // Also check groupId attribute (for consistency)
    if (item.containsKey("groupId")) {
      groupExperience.setGroupId(item.get("groupId").s());
    }
    if (item.containsKey("experienceId")) {
      groupExperience.setExperienceId(item.get("experienceId").s());
    }
    if (item.containsKey("sk")) {
      groupExperience.setCreatedAt(Instant.parse(item.get("sk").s()));
    }
    if (item.containsKey("updatedAt")) {
      groupExperience.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return groupExperience;
  }
}
