package com.yourafterspace.yas_backend.dao;

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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Data Access Object (DAO) for Experience entity operations in DynamoDB.
 *
 * <p>Why DAO? - Encapsulates all DynamoDB operations for experiences - Handles complex queries
 * including venue-based lookups - Reusable and testable
 *
 * <p>Table structure: PK: experienceId, SK: creationTime GSI1-PK: venueId, GSI1-SK: experienceId
 */
public class ExperienceDao {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public ExperienceDao(DynamoDbClient dynamoDbClient, String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Save an experience.
   *
   * @param experience Experience to save
   * @return Saved experience
   */
  public Experience save(Experience experience) {
    experience.setUpdatedAt(Instant.now());
    if (experience.getCreatedAt() == null) {
      experience.setCreatedAt(Instant.now());
    }

    Map<String, AttributeValue> item = toAttributeMap(experience);
    PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();
    dynamoDbClient.putItem(putRequest);

    return experience;
  }

  /**
   * Find an experience by experienceId.
   *
   * @param experienceId Experience ID
   * @return Optional containing the experience if found
   */
  public Optional<Experience> findByExperienceId(String experienceId) {
    // Use entity type prefix for single-table design
    // PK = "EXPERIENCE#experienceId", SK = createdAt
    String pk = "EXPERIENCE#" + experienceId;

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":pk", AttributeValue.builder().s(pk).build());
    // Add prefix for begins_with on sort key (createdAt) - using "2" to match years 2000+
    expressionAttributeValues.put(":skPrefix", AttributeValue.builder().s("2").build());

    // Query using userId as partition key (the table's PK attribute name)
    // Table structure: PK: pk, SK: sk
    // For composite keys, DynamoDB requires both PK and SK conditions
    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)") // pk is PK, sk is SK
            .expressionAttributeValues(expressionAttributeValues)
            .scanIndexForward(false) // Sort descending (latest first)
            .limit(1)
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
              .scanIndexForward(false)
              .limit(1)
              .build();
      response = dynamoDbClient.query(queryRequest);
    }

    if (response.items().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(fromAttributeMap(response.items().get(0)));
  }

  /**
   * Find all experiences for a venue (using GSI1).
   *
   * @param venueId Venue ID
   * @return List of experiences at that venue
   */
  public List<Experience> findByVenueId(String venueId) {
    try {
      Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
      expressionAttributeValues.put(":venueId", AttributeValue.builder().s(venueId).build());

      // GSI1 partition key is GSI1PK (must match venueId value)
      QueryRequest queryRequest =
          QueryRequest.builder()
              .tableName(tableName)
              .indexName("GSI1") // VenueId-ExperienceId index
              .keyConditionExpression("GSI1PK = :venueId")
              .expressionAttributeValues(expressionAttributeValues)
              .build();

      QueryResponse response = dynamoDbClient.query(queryRequest);

      System.out.println(
          "DEBUG: GSI1 query for venueId "
              + venueId
              + " returned "
              + response.items().size()
              + " items");

      List<Experience> experiences = new ArrayList<>();
      for (Map<String, AttributeValue> item : response.items()) {
        experiences.add(fromAttributeMap(item));
      }

      return experiences;
    } catch (Exception e) {
      // Log error but don't throw - return empty list instead
      System.err.println("Error querying GSI1 for venueId " + venueId + ": " + e.getMessage());
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  /** Convert Experience to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(Experience experience) {
    Map<String, AttributeValue> item = new HashMap<>();

    // Use entity type prefix for single-table design
    // PK = "EXPERIENCE#experienceId", SK = sk
    String pk = "EXPERIENCE#" + experience.getExperienceId();
    item.put("pk", AttributeValue.builder().s(pk).build()); // pk is the PK field
    item.put("sk", AttributeValue.builder().s(experience.getCreatedAt().toString()).build());

    item.put("experienceId", AttributeValue.builder().s(experience.getExperienceId()).build());

    if (experience.getCreatedBy() != null) {
      item.put("createdBy", AttributeValue.builder().s(experience.getCreatedBy()).build());
    }
    if (experience.getTitle() != null) {
      item.put("title", AttributeValue.builder().s(experience.getTitle()).build());
    }
    if (experience.getDescription() != null) {
      item.put("description", AttributeValue.builder().s(experience.getDescription()).build());
    }
    if (experience.getType() != null) {
      item.put("type", AttributeValue.builder().s(experience.getType().getValue()).build());
    }
    if (experience.getStatus() != null) {
      item.put("status", AttributeValue.builder().s(experience.getStatus().getValue()).build());
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
      item.put(
          "tags", AttributeValue.builder().ss(experience.getTags().toArray(new String[0])).build());
    }
    if (experience.getImages() != null && !experience.getImages().isEmpty()) {
      item.put(
          "images",
          AttributeValue.builder().ss(experience.getImages().toArray(new String[0])).build());
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
    if (experience.getUpdatedAt() != null) {
      item.put(
          "updatedAt", AttributeValue.builder().s(experience.getUpdatedAt().toString()).build());
    }

    // Store venueId if available (for linking to VenueLocation and GSI1 queries)
    // Note: venueId is stored as a regular attribute, not in the Experience model
    // It can be set via a Map<String, Object> when saving
    // This is handled in the handler layer

    return item;
  }

  /** Convert DynamoDB AttributeValue map to Experience. */
  private Experience fromAttributeMap(Map<String, AttributeValue> item) {
    Experience experience = new Experience();

    if (item.containsKey("experienceId")) {
      experience.setExperienceId(item.get("experienceId").s());
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
      experience.setLatitude(Double.parseDouble(item.get("latitude").n()));
    }
    if (item.containsKey("longitude")) {
      experience.setLongitude(Double.parseDouble(item.get("longitude").n()));
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
      experience.setMaxCapacity(Integer.parseInt(item.get("maxCapacity").n()));
    }
    if (item.containsKey("currentBookings")) {
      experience.setCurrentBookings(Integer.parseInt(item.get("currentBookings").n()));
    }
    if (item.containsKey("tags")) {
      experience.setTags(new ArrayList<>(item.get("tags").ss()));
    }
    if (item.containsKey("images")) {
      experience.setImages(new ArrayList<>(item.get("images").ss()));
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
      experience.setAverageRating(Double.parseDouble(item.get("averageRating").n()));
    }
    if (item.containsKey("totalReviews")) {
      experience.setTotalReviews(Integer.parseInt(item.get("totalReviews").n()));
    }
    // Read from sk (sort key) or createdAt (for backward compatibility)
    if (item.containsKey("sk")) {
      experience.setCreatedAt(Instant.parse(item.get("sk").s()));
    } else if (item.containsKey("createdAt")) {
      experience.setCreatedAt(Instant.parse(item.get("createdAt").s()));
    }
    if (item.containsKey("updatedAt")) {
      experience.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return experience;
  }
}
