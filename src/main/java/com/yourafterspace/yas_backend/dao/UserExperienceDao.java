package com.yourafterspace.yas_backend.dao;

import com.yourafterspace.yas_backend.model.UserExperience;
import com.yourafterspace.yas_backend.model.UserExperience.UserExperienceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Data Access Object (DAO) for UserExperience entity operations in DynamoDB.
 *
 * <p>Why DAO? - Handles user-experience relationships with additional metadata (interest, payment)
 * - Provides query methods filtered by status (interested, paid) - Encapsulates complex DynamoDB
 * operations
 *
 * <p>Table structure: PK: userId, SK: experienceId GSI1-PK: experienceId, GSI1-SK: userId
 */
public class UserExperienceDao {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public UserExperienceDao(DynamoDbClient dynamoDbClient, String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Save a user-experience relationship.
   *
   * @param userExperience UserExperience to save
   * @return Saved UserExperience
   */
  public UserExperience save(UserExperience userExperience) {
    userExperience.setUpdatedAt(Instant.now());
    if (userExperience.getCreatedAt() == null) {
      userExperience.setCreatedAt(Instant.now());
    }

    Map<String, AttributeValue> item = toAttributeMap(userExperience);
    System.out.println(
        "DEBUG: UserExperienceDao.save - userId=["
            + userExperience.getUserId()
            + "], experienceId=["
            + userExperience.getExperienceId()
            + "], status=["
            + (userExperience.getStatus() != null ? userExperience.getStatus().getValue() : "null")
            + "]");
    PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();
    dynamoDbClient.putItem(putRequest);
    System.out.println("DEBUG: UserExperienceDao.save - Record saved successfully");

    return userExperience;
  }

  /**
   * Find all experiences for a user.
   *
   * @param userId User ID
   * @return List of UserExperience relationships
   */
  public List<UserExperience> findByUserId(String userId) {
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":userId", AttributeValue.builder().s(userId).build());

    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :userId")
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    System.out.println(
        "DEBUG: UserExperienceDao.findByUserId - userId=["
            + userId
            + "], found "
            + response.items().size()
            + " items");
    for (Map<String, AttributeValue> item : response.items()) {
      String expId = item.containsKey("experienceId") ? item.get("experienceId").s() : "null";
      String status = item.containsKey("status") ? item.get("status").s() : "null";
      System.out.println(
          "DEBUG: UserExperience - experienceId=[" + expId + "], status=[" + status + "]");
    }

    List<UserExperience> userExperiences = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      // Filter out items that don't have an experienceId (e.g., UserProfile items)
      if (isValidUserExperienceItem(item)) {
        userExperiences.add(fromAttributeMap(item));
      }
    }

    return userExperiences;
  }

  /**
   * Find all experiences for a user filtered by status.
   *
   * @param userId User ID
   * @param status Status to filter by (INTERESTED, PAID, etc.)
   * @return List of UserExperience relationships matching the status
   */
  public List<UserExperience> findByUserIdAndStatus(String userId, UserExperienceStatus status) {
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":userId", AttributeValue.builder().s(userId).build());
    expressionAttributeValues.put(":status", AttributeValue.builder().s(status.getValue()).build());

    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :userId")
            .filterExpression("#status = :status")
            .expressionAttributeNames(Map.of("#status", "status"))
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    List<UserExperience> userExperiences = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      // Filter out items that don't have an experienceId (e.g., UserProfile items)
      if (isValidUserExperienceItem(item)) {
        userExperiences.add(fromAttributeMap(item));
      }
    }

    return userExperiences;
  }

  /**
   * Find all users for an experience (using GSI1).
   *
   * @param experienceId Experience ID
   * @return List of UserExperience relationships
   */
  public List<UserExperience> findByExperienceId(String experienceId) {
    // Remove EXPERIENCE# prefix if present - UserExperience stores experienceId without prefix
    String normalizedExperienceId =
        experienceId.startsWith("EXPERIENCE#")
            ? experienceId.replace("EXPERIENCE#", "")
            : experienceId;

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(
        ":experienceId", AttributeValue.builder().s(normalizedExperienceId).build());

    // Try GSI1 query first, but use scan as fallback if GSI1 doesn't exist or is misconfigured
    try {
      QueryRequest queryRequest =
          QueryRequest.builder()
              .tableName(tableName)
              .indexName("GSI1") // ExperienceId-UserId index
              .keyConditionExpression("GSI1PK = :experienceId") // Use GSI1PK as partition key name
              .expressionAttributeValues(expressionAttributeValues)
              .build();

      QueryResponse response = dynamoDbClient.query(queryRequest);

      List<UserExperience> userExperiences = new ArrayList<>();
      for (Map<String, AttributeValue> item : response.items()) {
        // Filter out items that don't have an experienceId (defensive check)
        if (isValidUserExperienceItem(item)) {
          userExperiences.add(fromAttributeMap(item));
        }
      }

      return userExperiences;
    } catch (Exception e) {
      // If GSI1 query fails, fall back to scan
      System.out.println(
          "DEBUG: GSI1 query failed for experienceId "
              + experienceId
              + ", falling back to scan: "
              + e.getMessage());
      e.printStackTrace();

      // Fallback: Use scan with filter expression
      // Try both with and without EXPERIENCE# prefix
      Map<String, AttributeValue> scanExpressionValues = new HashMap<>();
      scanExpressionValues.put(
          ":expId1", AttributeValue.builder().s(normalizedExperienceId).build());
      String withPrefix = "EXPERIENCE#" + normalizedExperienceId;
      scanExpressionValues.put(":expId2", AttributeValue.builder().s(withPrefix).build());

      ScanRequest scanRequest =
          ScanRequest.builder()
              .tableName(tableName)
              .filterExpression(
                  "attribute_exists(experienceId) AND (experienceId = :expId1 OR experienceId = :expId2)")
              .expressionAttributeValues(scanExpressionValues)
              .build();

      ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

      List<UserExperience> userExperiences = new ArrayList<>();
      for (Map<String, AttributeValue> item : scanResponse.items()) {
        if (isValidUserExperienceItem(item)) {
          userExperiences.add(fromAttributeMap(item));
        }
      }

      return userExperiences;
    }
  }

  /**
   * Find all users for an experience filtered by status (using GSI1).
   *
   * @param experienceId Experience ID
   * @param status Status to filter by
   * @return List of UserExperience relationships matching the status
   */
  public List<UserExperience> findByExperienceIdAndStatus(
      String experienceId, UserExperienceStatus status) {
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(
        ":experienceId", AttributeValue.builder().s(experienceId).build());
    expressionAttributeValues.put(":status", AttributeValue.builder().s(status.getValue()).build());

    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .indexName("GSI1")
            .keyConditionExpression("experienceId = :experienceId")
            .filterExpression("#status = :status")
            .expressionAttributeNames(Map.of("#status", "status"))
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    List<UserExperience> userExperiences = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      // Filter out items that don't have an experienceId (defensive check)
      if (isValidUserExperienceItem(item)) {
        userExperiences.add(fromAttributeMap(item));
      }
    }

    return userExperiences;
  }

  /**
   * Find all users who are interested in an experience (using GSI1, filtered by exp-interest =
   * true).
   *
   * @param experienceId Experience ID
   * @return List of UserExperience relationships where exp-interest = true
   */
  public List<UserExperience> findInterestedUsersByExperienceId(String experienceId) {
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(
        ":experienceId", AttributeValue.builder().s(experienceId).build());
    expressionAttributeValues.put(":expInterest", AttributeValue.builder().bool(true).build());

    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .indexName("GSI1") // ExperienceId-UserId index
            .keyConditionExpression("experienceId = :experienceId")
            .filterExpression("#expInterest = :expInterest")
            .expressionAttributeNames(Map.of("#expInterest", "exp-interest"))
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    System.out.println(
        "DEBUG: UserExperienceDao.findInterestedUsersByExperienceId - experienceId=["
            + experienceId
            + "], found "
            + response.items().size()
            + " items");

    List<UserExperience> userExperiences = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      // Filter out items that don't have an experienceId (defensive check)
      if (isValidUserExperienceItem(item)) {
        userExperiences.add(fromAttributeMap(item));
      }
    }

    return userExperiences;
  }

  /**
   * Find all UserExperience records where exp-interest = true (scan all records). Note: This is
   * expensive for large tables. Consider using pagination in production.
   *
   * @return List of UserExperience relationships where exp-interest = true
   */
  public List<UserExperience> findAllInterestedUsers() {
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":expInterest", AttributeValue.builder().bool(true).build());

    ScanRequest scanRequest =
        ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("#expInterest = :expInterest")
            .expressionAttributeNames(Map.of("#expInterest", "exp-interest"))
            .expressionAttributeValues(expressionAttributeValues)
            .build();

    ScanResponse response = dynamoDbClient.scan(scanRequest);

    System.out.println(
        "DEBUG: UserExperienceDao.findAllInterestedUsers - found "
            + response.items().size()
            + " items");

    List<UserExperience> userExperiences = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      // Filter out items that don't have an experienceId (e.g., UserProfile items)
      if (isValidUserExperienceItem(item)) {
        userExperiences.add(fromAttributeMap(item));
      }
    }

    // Handle pagination if needed (if response has lastEvaluatedKey)
    // For now, we'll just return the first page
    // TODO: Implement pagination for production use

    return userExperiences;
  }

  /** Convert UserExperience to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(UserExperience userExperience) {
    Map<String, AttributeValue> item = new HashMap<>();

    // PK: pk attribute = userId value (actual user ID)
    item.put("pk", AttributeValue.builder().s(userExperience.getUserId()).build());
    // Store userId as regular attribute for reference
    item.put("userId", AttributeValue.builder().s(userExperience.getUserId()).build());
    item.put("experienceId", AttributeValue.builder().s(userExperience.getExperienceId()).build());

    // Set GSI1 attributes for querying by experienceId
    // GSI1-PK: experienceId (as GSI1PK), GSI1-SK: userId (as GSI1SK)
    // Store experienceId as-is (without EXPERIENCE# prefix) in GSI1PK
    item.put("GSI1PK", AttributeValue.builder().s(userExperience.getExperienceId()).build());
    item.put("GSI1SK", AttributeValue.builder().s(userExperience.getUserId()).build());

    if (userExperience.getExperienceTime() != null) {
      item.put(
          "experienceTime",
          AttributeValue.builder().s(userExperience.getExperienceTime().toString()).build());
    }
    if (userExperience.getInterestScore() != null) {
      item.put(
          "interestScore",
          AttributeValue.builder().n(userExperience.getInterestScore().toString()).build());
    }
    if (userExperience.getExpInterest() != null) {
      item.put(
          "exp-interest", AttributeValue.builder().bool(userExperience.getExpInterest()).build());
    }
    if (userExperience.getStatus() != null) {
      item.put("status", AttributeValue.builder().s(userExperience.getStatus().getValue()).build());
    }
    if (userExperience.getPaid() != null) {
      item.put("PAID", AttributeValue.builder().bool(userExperience.getPaid()).build());
    }
    if (userExperience.getPaymentDetails() != null) {
      item.put("paymentDetails", toPaymentDetailsMap(userExperience.getPaymentDetails()));
    }
    // SK: sk attribute (required for composite key)
    if (userExperience.getCreatedAt() != null) {
      item.put("sk", AttributeValue.builder().s(userExperience.getCreatedAt().toString()).build());
    } else {
      // Fallback: set sk if somehow null
      item.put("sk", AttributeValue.builder().s(Instant.now().toString()).build());
    }
    if (userExperience.getUpdatedAt() != null) {
      item.put(
          "updatedAt",
          AttributeValue.builder().s(userExperience.getUpdatedAt().toString()).build());
    }

    return item;
  }

  /** Convert PaymentDetails to DynamoDB AttributeValue map. */
  private AttributeValue toPaymentDetailsMap(UserExperience.PaymentDetails paymentDetails) {
    Map<String, AttributeValue> paymentMap = new HashMap<>();

    if (paymentDetails.getAmount() != null) {
      paymentMap.put(
          "amount", AttributeValue.builder().n(paymentDetails.getAmount().toString()).build());
    }
    if (paymentDetails.getCurrency() != null) {
      paymentMap.put("currency", AttributeValue.builder().s(paymentDetails.getCurrency()).build());
    }
    if (paymentDetails.getPaymentMethod() != null) {
      paymentMap.put(
          "paymentMethod", AttributeValue.builder().s(paymentDetails.getPaymentMethod()).build());
    }
    if (paymentDetails.getPaymentDate() != null) {
      paymentMap.put(
          "paymentDate",
          AttributeValue.builder().s(paymentDetails.getPaymentDate().toString()).build());
    }
    if (paymentDetails.getTransactionId() != null) {
      paymentMap.put(
          "transactionId", AttributeValue.builder().s(paymentDetails.getTransactionId()).build());
    }

    return AttributeValue.builder().m(paymentMap).build();
  }

  /**
   * Check if an item is a valid UserExperience item (has a non-null, non-blank experienceId). This
   * filters out UserProfile items and other items that share the same table/partition key but are
   * not UserExperience items.
   *
   * @param item DynamoDB item map
   * @return true if the item has a valid experienceId, false otherwise
   */
  private boolean isValidUserExperienceItem(Map<String, AttributeValue> item) {
    if (!item.containsKey("experienceId")) {
      return false;
    }
    AttributeValue experienceIdValue = item.get("experienceId");
    if (experienceIdValue == null || experienceIdValue.s() == null) {
      return false;
    }
    String experienceId = experienceIdValue.s();
    return !experienceId.isBlank();
  }

  /** Convert DynamoDB AttributeValue map to UserExperience. */
  private UserExperience fromAttributeMap(Map<String, AttributeValue> item) {
    UserExperience userExperience = new UserExperience();

    // Read userId from pk (partition key) or userId attribute
    if (item.containsKey("pk")) {
      userExperience.setUserId(item.get("pk").s());
    } else if (item.containsKey("userId")) {
      userExperience.setUserId(item.get("userId").s());
    }
    if (item.containsKey("experienceId")) {
      userExperience.setExperienceId(item.get("experienceId").s());
    }
    if (item.containsKey("experienceTime")) {
      userExperience.setExperienceTime(Instant.parse(item.get("experienceTime").s()));
    }
    if (item.containsKey("interestScore")) {
      userExperience.setInterestScore(Double.parseDouble(item.get("interestScore").n()));
    }
    if (item.containsKey("exp-interest")) {
      userExperience.setExpInterest(item.get("exp-interest").bool());
    }
    if (item.containsKey("status")) {
      userExperience.setStatus(UserExperienceStatus.fromValue(item.get("status").s()));
    }
    // If PAID field exists in DB, use it (overrides what setStatus may have set)
    if (item.containsKey("PAID")) {
      userExperience.setPaid(item.get("PAID").bool());
    }
    if (item.containsKey("paymentDetails")) {
      userExperience.setPaymentDetails(fromPaymentDetailsMap(item.get("paymentDetails").m()));
    }
    // Read from sk (sort key) or createdAt (for backward compatibility)
    if (item.containsKey("sk")) {
      userExperience.setCreatedAt(Instant.parse(item.get("sk").s()));
    } else if (item.containsKey("createdAt")) {
      userExperience.setCreatedAt(Instant.parse(item.get("createdAt").s()));
    }
    if (item.containsKey("updatedAt")) {
      userExperience.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return userExperience;
  }

  /** Convert DynamoDB AttributeValue map to PaymentDetails. */
  private UserExperience.PaymentDetails fromPaymentDetailsMap(Map<String, AttributeValue> map) {
    UserExperience.PaymentDetails paymentDetails = new UserExperience.PaymentDetails();

    if (map.containsKey("amount")) {
      paymentDetails.setAmount(new BigDecimal(map.get("amount").n()));
    }
    if (map.containsKey("currency")) {
      paymentDetails.setCurrency(map.get("currency").s());
    }
    if (map.containsKey("paymentMethod")) {
      paymentDetails.setPaymentMethod(map.get("paymentMethod").s());
    }
    if (map.containsKey("paymentDate")) {
      paymentDetails.setPaymentDate(Instant.parse(map.get("paymentDate").s()));
    }
    if (map.containsKey("transactionId")) {
      paymentDetails.setTransactionId(map.get("transactionId").s());
    }

    return paymentDetails;
  }
}
