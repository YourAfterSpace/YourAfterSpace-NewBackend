package com.yourafterspace.yas_backend.repository;

import com.yourafterspace.yas_backend.model.UserProfile;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Repository for DynamoDB operations on user profiles.
 *
 * <p>This repository handles all DynamoDB interactions for user profile data. The table uses a
 * composite key: userId (partition key) and createdAt (sort key).
 */
@Repository
public class UserProfileRepository {

  private static final Logger logger = LoggerFactory.getLogger(UserProfileRepository.class);
  private static final String TABLE_NAME_PROPERTY = "aws.dynamodb.user-profile-table";
  private static final String DEFAULT_TABLE_NAME = "user-profiles";

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public UserProfileRepository(
      DynamoDbClient dynamoDbClient,
      @Value("${" + TABLE_NAME_PROPERTY + ":" + DEFAULT_TABLE_NAME + "}") String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
    logger.info("UserProfileRepository initialized with table name: {}", this.tableName);
  }

  /**
   * Save or update a user profile.
   *
   * <p>If the profile already exists (has createdAt), it will be updated. Otherwise, a new profile
   * will be created with the current timestamp as the sort key.
   *
   * @param profile User profile to save
   * @return Saved user profile
   */
  public UserProfile save(UserProfile profile) {
    profile.setUpdatedAt(Instant.now());
    if (profile.getCreatedAt() == null) {
      profile.setCreatedAt(Instant.now());
    }

    // Check if profile already exists
    Optional<UserProfile> existing = findByUserId(profile.getUserId());
    if (existing.isPresent()) {
      // Update existing profile - preserve original createdAt
      UserProfile existingProfile = existing.get();
      profile.setCreatedAt(existingProfile.getCreatedAt());
      updateProfile(profile);
    } else {
      // Create new profile
      createProfile(profile);
    }

    logger.debug("Saved user profile for userId: {}", profile.getUserId());
    return profile;
  }

  /**
   * Create a new user profile.
   *
   * @param profile User profile to create
   */
  private void createProfile(UserProfile profile) {
    Map<String, AttributeValue> item = toAttributeMap(profile);
    PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();
    dynamoDbClient.putItem(putRequest);
    logger.debug("Created new user profile for userId: {}", profile.getUserId());
  }

  /**
   * Update an existing user profile.
   *
   * @param profile User profile to update (must have userId and createdAt)
   */
  private void updateProfile(UserProfile profile) {
    Map<String, AttributeValue> key =
        buildCompositeKey(profile.getUserId(), profile.getCreatedAt());

    // Build update expression using expression attribute names to avoid reserved keyword conflicts
    // Expression attribute names (e.g., #state) are used for attribute names
    // Expression attribute values (e.g., :state) are used for attribute values
    Map<String, String> expressionAttributeNames = new HashMap<>();
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

    StringBuilder updateExpression = new StringBuilder("SET #updatedAt = :updatedAt");
    expressionAttributeNames.put("#updatedAt", "updatedAt");
    expressionAttributeValues.put(
        ":updatedAt", AttributeValue.builder().s(profile.getUpdatedAt().toString()).build());

    // Add optional fields to update
    if (profile.getDateOfBirth() != null) {
      updateExpression.append(", #dateOfBirth = :dateOfBirth");
      expressionAttributeNames.put("#dateOfBirth", "dateOfBirth");
      expressionAttributeValues.put(
          ":dateOfBirth", AttributeValue.builder().s(profile.getDateOfBirth().toString()).build());
    }
    if (profile.getAddress() != null) {
      updateExpression.append(", #address = :address");
      expressionAttributeNames.put("#address", "address");
      expressionAttributeValues.put(
          ":address", AttributeValue.builder().s(profile.getAddress()).build());
    }
    if (profile.getCity() != null) {
      updateExpression.append(", #city = :city");
      expressionAttributeNames.put("#city", "city");
      expressionAttributeValues.put(":city", AttributeValue.builder().s(profile.getCity()).build());
    }
    if (profile.getState() != null) {
      updateExpression.append(", #state = :state");
      expressionAttributeNames.put("#state", "state");
      expressionAttributeValues.put(
          ":state", AttributeValue.builder().s(profile.getState()).build());
    }
    if (profile.getZipCode() != null) {
      updateExpression.append(", #zipCode = :zipCode");
      expressionAttributeNames.put("#zipCode", "zipCode");
      expressionAttributeValues.put(
          ":zipCode", AttributeValue.builder().s(profile.getZipCode()).build());
    }
    if (profile.getCountry() != null) {
      updateExpression.append(", #country = :country");
      expressionAttributeNames.put("#country", "country");
      expressionAttributeValues.put(
          ":country", AttributeValue.builder().s(profile.getCountry()).build());
    }
    if (profile.getGender() != null) {
      updateExpression.append(", #gender = :gender");
      expressionAttributeNames.put("#gender", "gender");
      expressionAttributeValues.put(
          ":gender", AttributeValue.builder().s(profile.getGender()).build());
    }
    if (profile.getProfession() != null) {
      updateExpression.append(", #profession = :profession");
      expressionAttributeNames.put("#profession", "profession");
      expressionAttributeValues.put(
          ":profession", AttributeValue.builder().s(profile.getProfession()).build());
    }
    if (profile.getCompany() != null) {
      updateExpression.append(", #company = :company");
      expressionAttributeNames.put("#company", "company");
      expressionAttributeValues.put(
          ":company", AttributeValue.builder().s(profile.getCompany()).build());
    }
    if (profile.getBio() != null) {
      updateExpression.append(", #bio = :bio");
      expressionAttributeNames.put("#bio", "bio");
      expressionAttributeValues.put(":bio", AttributeValue.builder().s(profile.getBio()).build());
    }
    if (profile.getPhoneNumber() != null) {
      updateExpression.append(", #phoneNumber = :phoneNumber");
      expressionAttributeNames.put("#phoneNumber", "phoneNumber");
      expressionAttributeValues.put(
          ":phoneNumber", AttributeValue.builder().s(profile.getPhoneNumber()).build());
    }
    if (profile.getStatus() != null) {
      updateExpression.append(", #status = :status");
      expressionAttributeNames.put("#status", "status");
      expressionAttributeValues.put(
          ":status", AttributeValue.builder().s(profile.getStatus().getValue()).build());
    }

    UpdateItemRequest.Builder updateRequestBuilder =
        UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpression.toString())
            .expressionAttributeValues(expressionAttributeValues);

    // Only add expressionAttributeNames if we have any (required when using #attributeName syntax)
    if (!expressionAttributeNames.isEmpty()) {
      updateRequestBuilder.expressionAttributeNames(expressionAttributeNames);
    }

    dynamoDbClient.updateItem(updateRequestBuilder.build());
    logger.debug("Updated user profile for userId: {}", profile.getUserId());
  }

  /**
   * Find an active user profile by userId. Returns the latest profile (most recent createdAt). This
   * method filters out soft-deleted users.
   *
   * @param userId Cognito user ID
   * @return Optional containing the user profile if found and active
   */
  public Optional<UserProfile> findByUserId(String userId) {
    Optional<UserProfile> profile = findByUserIdIncludingDeleted(userId);
    return profile.filter(UserProfile::isActive);
  }

  /**
   * Find a user profile by userId including soft-deleted users. Returns the latest profile (most
   * recent createdAt).
   *
   * @param userId Cognito user ID
   * @return Optional containing the user profile if found (including deleted)
   */
  public Optional<UserProfile> findByUserIdIncludingDeleted(String userId) {
    // Query by userId (partition key) and get the latest item (highest createdAt)
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":userId", AttributeValue.builder().s(userId).build());

    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("userId = :userId")
            .expressionAttributeValues(expressionAttributeValues)
            .scanIndexForward(false) // Sort descending (latest first)
            .limit(1) // Get only the latest profile
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    if (response.items().isEmpty()) {
      logger.debug("User profile not found for userId: {}", userId);
      return Optional.empty();
    }

    UserProfile profile = fromAttributeMap(response.items().get(0));
    logger.debug("Found user profile for userId: {}", userId);
    return Optional.of(profile);
  }

  /**
   * Build composite key for DynamoDB operations.
   *
   * @param userId Partition key
   * @param createdAt Sort key
   * @return Map containing the composite key
   */
  private Map<String, AttributeValue> buildCompositeKey(String userId, Instant createdAt) {
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("userId", AttributeValue.builder().s(userId).build());
    key.put("createdAt", AttributeValue.builder().s(createdAt.toString()).build());
    return key;
  }

  /**
   * Soft delete a user profile by setting status to DELETED.
   *
   * @param userId Cognito user ID
   * @return true if user was found and soft deleted, false if not found
   */
  public boolean softDeleteByUserId(String userId) {
    Optional<UserProfile> existingProfile = findByUserIdIncludingDeleted(userId);
    if (existingProfile.isPresent()) {
      UserProfile profile = existingProfile.get();
      profile.delete(); // Sets status to DELETED and updates timestamp
      save(profile);
      logger.info("Soft deleted user profile for userId: {}", userId);
      return true;
    }
    logger.warn("Cannot soft delete - user profile not found for userId: {}", userId);
    return false;
  }

  /**
   * Reactivate a soft-deleted user profile.
   *
   * @param userId Cognito user ID
   * @return true if user was found and reactivated, false if not found
   */
  public boolean reactivateByUserId(String userId) {
    Optional<UserProfile> existingProfile = findByUserIdIncludingDeleted(userId);
    if (existingProfile.isPresent()) {
      UserProfile profile = existingProfile.get();
      profile.activate(); // Sets status to ACTIVE and updates timestamp
      save(profile);
      logger.info("Reactivated user profile for userId: {}", userId);
      return true;
    }
    logger.warn("Cannot reactivate - user profile not found for userId: {}", userId);
    return false;
  }

  /**
   * Check if an active user profile exists.
   *
   * @param userId Cognito user ID
   * @return true if profile exists and is active, false otherwise
   */
  public boolean existsByUserId(String userId) {
    return findByUserId(userId).isPresent();
  }

  /**
   * Check if a user profile exists (including soft-deleted ones).
   *
   * @param userId Cognito user ID
   * @return true if profile exists (including deleted), false otherwise
   */
  public boolean existsByUserIdIncludingDeleted(String userId) {
    return findByUserIdIncludingDeleted(userId).isPresent();
  }

  /**
   * Convert UserProfile to DynamoDB AttributeValue map.
   *
   * @param profile User profile
   * @return Map of attribute values
   */
  private Map<String, AttributeValue> toAttributeMap(UserProfile profile) {
    Map<String, AttributeValue> item = new HashMap<>();

    // Composite key: userId (partition) + createdAt (sort)
    item.put("userId", AttributeValue.builder().s(profile.getUserId()).build());
    item.put("createdAt", AttributeValue.builder().s(profile.getCreatedAt().toString()).build());

    if (profile.getDateOfBirth() != null) {
      item.put(
          "dateOfBirth", AttributeValue.builder().s(profile.getDateOfBirth().toString()).build());
    }

    if (profile.getAddress() != null) {
      item.put("address", AttributeValue.builder().s(profile.getAddress()).build());
    }

    if (profile.getCity() != null) {
      item.put("city", AttributeValue.builder().s(profile.getCity()).build());
    }

    if (profile.getState() != null) {
      item.put("state", AttributeValue.builder().s(profile.getState()).build());
    }

    if (profile.getZipCode() != null) {
      item.put("zipCode", AttributeValue.builder().s(profile.getZipCode()).build());
    }

    if (profile.getCountry() != null) {
      item.put("country", AttributeValue.builder().s(profile.getCountry()).build());
    }

    if (profile.getGender() != null) {
      item.put("gender", AttributeValue.builder().s(profile.getGender()).build());
    }

    if (profile.getProfession() != null) {
      item.put("profession", AttributeValue.builder().s(profile.getProfession()).build());
    }

    if (profile.getCompany() != null) {
      item.put("company", AttributeValue.builder().s(profile.getCompany()).build());
    }

    if (profile.getBio() != null) {
      item.put("bio", AttributeValue.builder().s(profile.getBio()).build());
    }

    if (profile.getPhoneNumber() != null) {
      item.put("phoneNumber", AttributeValue.builder().s(profile.getPhoneNumber()).build());
    }

    if (profile.getStatus() != null) {
      item.put("status", AttributeValue.builder().s(profile.getStatus().getValue()).build());
    }

    // createdAt is already added as part of the composite key above
    // updatedAt is a regular attribute
    if (profile.getUpdatedAt() != null) {
      item.put("updatedAt", AttributeValue.builder().s(profile.getUpdatedAt().toString()).build());
    }

    return item;
  }

  /**
   * Convert DynamoDB AttributeValue map to UserProfile.
   *
   * @param item Map of attribute values
   * @return User profile
   */
  private UserProfile fromAttributeMap(Map<String, AttributeValue> item) {
    UserProfile profile = new UserProfile();

    if (item.containsKey("userId")) {
      profile.setUserId(item.get("userId").s());
    }

    if (item.containsKey("dateOfBirth")) {
      profile.setDateOfBirth(LocalDate.parse(item.get("dateOfBirth").s()));
    }

    if (item.containsKey("address")) {
      profile.setAddress(item.get("address").s());
    }

    if (item.containsKey("city")) {
      profile.setCity(item.get("city").s());
    }

    if (item.containsKey("state")) {
      profile.setState(item.get("state").s());
    }

    if (item.containsKey("zipCode")) {
      profile.setZipCode(item.get("zipCode").s());
    }

    if (item.containsKey("country")) {
      profile.setCountry(item.get("country").s());
    }

    if (item.containsKey("gender")) {
      profile.setGender(item.get("gender").s());
    }

    if (item.containsKey("profession")) {
      profile.setProfession(item.get("profession").s());
    }

    if (item.containsKey("company")) {
      profile.setCompany(item.get("company").s());
    }

    if (item.containsKey("bio")) {
      profile.setBio(item.get("bio").s());
    }

    if (item.containsKey("phoneNumber")) {
      profile.setPhoneNumber(item.get("phoneNumber").s());
    }

    if (item.containsKey("status")) {
      profile.setStatus(UserStatus.fromValue(item.get("status").s()));
    } else {
      // Default to ACTIVE for existing records without status
      profile.setStatus(UserStatus.ACTIVE);
    }

    if (item.containsKey("createdAt")) {
      profile.setCreatedAt(Instant.parse(item.get("createdAt").s()));
    }

    if (item.containsKey("updatedAt")) {
      profile.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return profile;
  }
}
