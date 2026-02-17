package com.yourafterspace.yas_backend.repository;

import com.yourafterspace.yas_backend.model.Experience;
import com.yourafterspace.yas_backend.model.Experience.ExperienceStatus;
import com.yourafterspace.yas_backend.model.Experience.ExperienceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Repository for DynamoDB operations on experiences.
 *
 * <p>This repository handles all DynamoDB interactions for experience data. The table uses: -
 * Primary Key: experienceId (partition key) - GSI: createdBy-createdAt-index for querying
 * experiences by creator - GSI: type-experienceDate-index for querying experiences by type and date
 */
@Repository
public class ExperienceRepository {

  private static final Logger logger = LoggerFactory.getLogger(ExperienceRepository.class);
  private static final String TABLE_NAME_PROPERTY = "aws.dynamodb.user-profile-table";
  private static final String DEFAULT_TABLE_NAME = "YourAfterSpace";

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public ExperienceRepository(
      DynamoDbClient dynamoDbClient,
      @Value("${" + TABLE_NAME_PROPERTY + ":" + DEFAULT_TABLE_NAME + "}") String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
    logger.info("ExperienceRepository initialized with table name: {}", this.tableName);
  }

  /**
   * Save or update an experience.
   *
   * @param experience Experience to save
   * @return Saved experience
   */
  public Experience save(Experience experience) {
    experience.setUpdatedAt(Instant.now());

    // Generate ID if new experience
    if (experience.getExperienceId() == null) {
      // Use "exp-" prefix to avoid conflicts with user profiles in the same table
      experience.setExperienceId("exp-" + UUID.randomUUID().toString());
      experience.setCreatedAt(Instant.now());
    }

    Map<String, AttributeValue> item = toAttributeMap(experience);
    PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();

    dynamoDbClient.putItem(putRequest);
    logger.debug("Saved experience with ID: {}", experience.getExperienceId());
    return experience;
  }

  /**
   * Find an experience by ID.
   *
   * @param experienceId Experience ID
   * @return Optional containing the experience if found
   */
  public Optional<Experience> findById(String experienceId) {
    try {
      logger.debug("Searching for experience with ID: {}", experienceId);

      // Since we don't know the createdAt timestamp, scan with filter for userId
      Map<String, AttributeValue> expressionAttributeValues =
          Map.of(":experienceId", AttributeValue.builder().s(experienceId).build());

      software.amazon.awssdk.services.dynamodb.model.ScanRequest scanRequest =
          software.amazon.awssdk.services.dynamodb.model.ScanRequest.builder()
              .tableName(tableName)
              .filterExpression("userId = :experienceId")
              .expressionAttributeValues(expressionAttributeValues)
              .build();

      var response = dynamoDbClient.scan(scanRequest);

      for (Map<String, AttributeValue> item : response.items()) {
        // Check if it's a user profile vs experience
        if (item.containsKey("email")
            || item.containsKey("firstName")
            || item.containsKey("lastName")) {
          logger.debug("Skipping user profile record for userId: {}", experienceId);
          continue;
        }

        // If recordType exists and is not EXPERIENCE, skip it
        if (item.containsKey("recordType") && !"EXPERIENCE".equals(item.get("recordType").s())) {
          logger.debug("Skipping record with recordType: {}", item.get("recordType").s());
          continue;
        }

        // Convert to Experience and return
        Experience experience = fromAttributeMap(item);
        logger.debug("Found experience for ID: {}", experienceId);
        return Optional.of(experience);
      }

      logger.debug("Experience not found for ID: {}", experienceId);
      return Optional.empty();

    } catch (Exception e) {
      logger.error("Error finding experience by ID {}: {}", experienceId, e.getMessage(), e);
      throw new RuntimeException("Failed to find experience: " + e.getMessage(), e);
    }
  }

  /**
   * Check if an experience exists.
   *
   * @param experienceId Experience ID
   * @return true if experience exists, false otherwise
   */
  public boolean existsById(String experienceId) {
    return findById(experienceId).isPresent();
  }

  /**
   * Find all experiences in the database (scan for recordType = EXPERIENCE).
   *
   * @return List of all experiences
   */
  public List<Experience> findAll() {
    List<Experience> results = new ArrayList<>();
    Map<String, AttributeValue> lastKey = null;

    do {
      ScanRequest.Builder scanBuilder =
          ScanRequest.builder()
              .tableName(tableName)
              .filterExpression("#rt = :recordType")
              .expressionAttributeNames(Map.of("#rt", "recordType"))
              .expressionAttributeValues(
                  Map.of(
                      ":recordType",
                      AttributeValue.builder().s("EXPERIENCE").build()));

      if (lastKey != null && !lastKey.isEmpty()) {
        scanBuilder.exclusiveStartKey(lastKey);
      }

      ScanResponse response = dynamoDbClient.scan(scanBuilder.build());

      for (Map<String, AttributeValue> item : response.items()) {
        try {
          results.add(fromAttributeMap(item));
        } catch (Exception e) {
          logger.warn("Skipping item that could not be mapped to Experience: {}", e.getMessage());
        }
      }

      lastKey = response.lastEvaluatedKey();
    } while (lastKey != null && !lastKey.isEmpty());

    logger.debug("Found {} experiences in total", results.size());
    return results;
  }

  /**
   * Find all experiences in the given city (scan for recordType = EXPERIENCE and city = city).
   * City comparison is case-insensitive after fetch (DynamoDB has no case-insensitive filter).
   *
   * @param city City name to filter by (e.g. "Mumbai")
   * @return List of experiences in that city
   */
  public List<Experience> findAllByCity(String city) {
    if (city == null || city.isBlank()) {
      return findAll();
    }
    List<Experience> results = new ArrayList<>();
    Map<String, AttributeValue> lastKey = null;
    Map<String, String> attrNames = new HashMap<>();
    attrNames.put("#rt", "recordType");
    attrNames.put("#city", "city");
    Map<String, AttributeValue> attrValues = new HashMap<>();
    attrValues.put(":recordType", AttributeValue.builder().s("EXPERIENCE").build());
    attrValues.put(":city", AttributeValue.builder().s(city.trim()).build());

    do {
      ScanRequest.Builder scanBuilder =
          ScanRequest.builder()
              .tableName(tableName)
              .filterExpression("#rt = :recordType AND #city = :city")
              .expressionAttributeNames(attrNames)
              .expressionAttributeValues(attrValues);

      if (lastKey != null && !lastKey.isEmpty()) {
        scanBuilder.exclusiveStartKey(lastKey);
      }

      ScanResponse response = dynamoDbClient.scan(scanBuilder.build());

      for (Map<String, AttributeValue> item : response.items()) {
        try {
          results.add(fromAttributeMap(item));
        } catch (Exception e) {
          logger.warn("Skipping item that could not be mapped to Experience: {}", e.getMessage());
        }
      }

      lastKey = response.lastEvaluatedKey();
    } while (lastKey != null && !lastKey.isEmpty());

    logger.debug("Found {} experiences in city {}", results.size(), city);
    return results;
  }

  /** Convert Experience to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(Experience experience) {
    Map<String, AttributeValue> item = new HashMap<>();

    // Composite key - use experienceId as userId in shared table
    item.put("userId", AttributeValue.builder().s(experience.getExperienceId()).build());
    // Use the experience's createdAt as sort key
    item.put("createdAt", AttributeValue.builder().s(experience.getCreatedAt().toString()).build());

    // Add type indicator to distinguish from user profiles
    item.put("recordType", AttributeValue.builder().s("EXPERIENCE").build());

    // Required fields
    if (experience.getCreatedBy() != null) {
      item.put("createdBy", AttributeValue.builder().s(experience.getCreatedBy()).build());
    }

    if (experience.getTitle() != null) {
      item.put("title", AttributeValue.builder().s(experience.getTitle()).build());
    }

    if (experience.getType() != null) {
      item.put("type", AttributeValue.builder().s(experience.getType().getValue()).build());
    }

    if (experience.getStatus() != null) {
      item.put("status", AttributeValue.builder().s(experience.getStatus().getValue()).build());
      logger.debug("Including status in save: {}", experience.getStatus().getValue());
    } else {
      logger.warn(
          "Status is null for experience {}, not saving status field",
          experience.getExperienceId());
    }

    // Optional fields
    if (experience.getDescription() != null) {
      item.put("description", AttributeValue.builder().s(experience.getDescription()).build());
    }

    if (experience.getLocation() != null) {
      item.put("location", AttributeValue.builder().s(experience.getLocation()).build());
    }

    if (experience.getAddress() != null) {
      item.put("address", AttributeValue.builder().s(experience.getAddress()).build());
    }

    if (experience.getCity() != null) {
      item.put("city", AttributeValue.builder().s(experience.getCity()).build());
    }

    if (experience.getCountry() != null) {
      item.put("country", AttributeValue.builder().s(experience.getCountry()).build());
    }

    if (experience.getLatitude() != null) {
      item.put("latitude", AttributeValue.builder().n(experience.getLatitude().toString()).build());
    }

    if (experience.getLongitude() != null) {
      item.put(
          "longitude", AttributeValue.builder().n(experience.getLongitude().toString()).build());
    }

    if (experience.getExperienceDate() != null) {
      item.put(
          "experienceDate",
          AttributeValue.builder().s(experience.getExperienceDate().toString()).build());
    }

    if (experience.getStartTime() != null) {
      item.put(
          "startTime", AttributeValue.builder().s(experience.getStartTime().toString()).build());
    }

    if (experience.getEndTime() != null) {
      item.put("endTime", AttributeValue.builder().s(experience.getEndTime().toString()).build());
    }

    if (experience.getPricePerPerson() != null) {
      item.put(
          "pricePerPerson",
          AttributeValue.builder().n(experience.getPricePerPerson().toString()).build());
    }

    if (experience.getCurrency() != null) {
      item.put("currency", AttributeValue.builder().s(experience.getCurrency()).build());
    }

    if (experience.getMaxCapacity() != null) {
      item.put(
          "maxCapacity",
          AttributeValue.builder().n(experience.getMaxCapacity().toString()).build());
    }

    if (experience.getCurrentBookings() != null) {
      item.put(
          "currentBookings",
          AttributeValue.builder().n(experience.getCurrentBookings().toString()).build());
    }

    if (experience.getTags() != null && !experience.getTags().isEmpty()) {
      List<AttributeValue> tagList =
          experience.getTags().stream()
              .map(tag -> AttributeValue.builder().s(tag).build())
              .toList();
      item.put("tags", AttributeValue.builder().l(tagList).build());
    }

    if (experience.getImages() != null && !experience.getImages().isEmpty()) {
      List<AttributeValue> imageList =
          experience.getImages().stream()
              .map(image -> AttributeValue.builder().s(image).build())
              .toList();
      item.put("images", AttributeValue.builder().l(imageList).build());
    }

    if (experience.getContactInfo() != null) {
      item.put("contactInfo", AttributeValue.builder().s(experience.getContactInfo()).build());
    }

    if (experience.getRequirements() != null) {
      item.put("requirements", AttributeValue.builder().s(experience.getRequirements()).build());
    }

    if (experience.getCancellationPolicy() != null) {
      item.put(
          "cancellationPolicy",
          AttributeValue.builder().s(experience.getCancellationPolicy()).build());
    }

    if (experience.getAverageRating() != null) {
      item.put(
          "averageRating",
          AttributeValue.builder().n(experience.getAverageRating().toString()).build());
    }

    if (experience.getTotalReviews() != null) {
      item.put(
          "totalReviews",
          AttributeValue.builder().n(experience.getTotalReviews().toString()).build());
    }

    // Timestamps
    if (experience.getCreatedAt() != null) {
      item.put(
          "createdAt", AttributeValue.builder().s(experience.getCreatedAt().toString()).build());
    }

    if (experience.getUpdatedAt() != null) {
      item.put(
          "updatedAt", AttributeValue.builder().s(experience.getUpdatedAt().toString()).build());
    }

    return item;
  }

  /** Convert DynamoDB AttributeValue map to Experience. */
  private Experience fromAttributeMap(Map<String, AttributeValue> item) {
    Experience experience = new Experience();

    if (item.containsKey("userId")) {
      experience.setExperienceId(item.get("userId").s());
    }

    if (item.containsKey("createdBy")) {
      experience.setCreatedBy(item.get("createdBy").s());
    }

    if (item.containsKey("title")) {
      experience.setTitle(item.get("title").s());
    }

    if (item.containsKey("description")) {
      experience.setDescription(item.get("description").s());
    }

    if (item.containsKey("type")) {
      experience.setType(ExperienceType.fromValue(item.get("type").s()));
    }

    if (item.containsKey("status")) {
      experience.setStatus(ExperienceStatus.fromValue(item.get("status").s()));
    }

    if (item.containsKey("location")) {
      experience.setLocation(item.get("location").s());
    }

    if (item.containsKey("address")) {
      experience.setAddress(item.get("address").s());
    }

    if (item.containsKey("city")) {
      experience.setCity(item.get("city").s());
    }

    if (item.containsKey("country")) {
      experience.setCountry(item.get("country").s());
    }

    if (item.containsKey("latitude")) {
      experience.setLatitude(Double.valueOf(item.get("latitude").n()));
    }

    if (item.containsKey("longitude")) {
      experience.setLongitude(Double.valueOf(item.get("longitude").n()));
    }

    if (item.containsKey("experienceDate")) {
      experience.setExperienceDate(LocalDate.parse(item.get("experienceDate").s()));
    }

    if (item.containsKey("startTime")) {
      experience.setStartTime(LocalTime.parse(item.get("startTime").s()));
    }

    if (item.containsKey("endTime")) {
      experience.setEndTime(LocalTime.parse(item.get("endTime").s()));
    }

    if (item.containsKey("pricePerPerson")) {
      experience.setPricePerPerson(new BigDecimal(item.get("pricePerPerson").n()));
    }

    if (item.containsKey("currency")) {
      experience.setCurrency(item.get("currency").s());
    }

    if (item.containsKey("maxCapacity")) {
      experience.setMaxCapacity(Integer.valueOf(item.get("maxCapacity").n()));
    }

    if (item.containsKey("currentBookings")) {
      experience.setCurrentBookings(Integer.valueOf(item.get("currentBookings").n()));
    }

    if (item.containsKey("tags")) {
      List<String> tags = item.get("tags").l().stream().map(AttributeValue::s).toList();
      experience.setTags(tags);
    }

    if (item.containsKey("images")) {
      List<String> images = item.get("images").l().stream().map(AttributeValue::s).toList();
      experience.setImages(images);
    }

    if (item.containsKey("contactInfo")) {
      experience.setContactInfo(item.get("contactInfo").s());
    }

    if (item.containsKey("requirements")) {
      experience.setRequirements(item.get("requirements").s());
    }

    if (item.containsKey("cancellationPolicy")) {
      experience.setCancellationPolicy(item.get("cancellationPolicy").s());
    }

    if (item.containsKey("averageRating")) {
      experience.setAverageRating(Double.valueOf(item.get("averageRating").n()));
    }

    if (item.containsKey("totalReviews")) {
      experience.setTotalReviews(Integer.valueOf(item.get("totalReviews").n()));
    }

    if (item.containsKey("createdAt")) {
      experience.setCreatedAt(Instant.parse(item.get("createdAt").s()));
    }

    if (item.containsKey("updatedAt")) {
      experience.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return experience;
  }
}
