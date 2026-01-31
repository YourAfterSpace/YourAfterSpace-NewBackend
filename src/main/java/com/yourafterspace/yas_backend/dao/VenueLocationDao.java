package com.yourafterspace.yas_backend.dao;

import com.yourafterspace.yas_backend.model.VenueLocation;
import com.yourafterspace.yas_backend.util.GeohashUtil;
import java.time.Instant;
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
 * Data Access Object (DAO) for VenueLocation entity operations in DynamoDB.
 *
 * <p>Why DAO? - Encapsulates all DynamoDB operations for venues - Handles geohash calculations
 * automatically - Can be reused across different contexts (Lambda, Spring, etc.) - Easy to test and
 * mock
 *
 * <p>Table structure: PK: venueId, SK: creationTime GSI3-PK: geohash_prefix (for nearby venue
 * queries)
 */
public class VenueLocationDao {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public VenueLocationDao(DynamoDbClient dynamoDbClient, String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Save a venue location. Automatically calculates and stores geohash_prefix.
   *
   * @param venue Venue location to save
   * @return Saved venue location
   */
  public VenueLocation save(VenueLocation venue) {
    venue.setUpdatedAt(Instant.now());
    if (venue.getCreationTime() == null) {
      venue.setCreationTime(Instant.now());
    }
    if (venue.getCreatedAt() == null) {
      venue.setCreatedAt(Instant.now());
    }

    // Calculate and store geohash prefix if lat/lon are provided
    if (venue.getLatitude() != null && venue.getLongitude() != null) {
      venue.setGeohashPrefix(
          GeohashUtil.getGeohashPrefix(venue.getLatitude(), venue.getLongitude()));
    }

    Map<String, AttributeValue> item = toAttributeMap(venue);
    PutItemRequest putRequest = PutItemRequest.builder().tableName(tableName).item(item).build();
    dynamoDbClient.putItem(putRequest);

    return venue;
  }

  /**
   * Find a venue by venueId.
   *
   * @param venueId Venue ID
   * @return Optional containing the venue if found
   */
  public Optional<VenueLocation> findByVenueId(String venueId) {
    // Use entity type prefix for single-table design
    // PK = "VENUE#venueId", SK = creationTime
    String pk = "VENUE#" + venueId;

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":pk", AttributeValue.builder().s(pk).build());

    QueryRequest queryRequest =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk") // pk is the PK field in the table
            .expressionAttributeValues(expressionAttributeValues)
            .scanIndexForward(false) // Sort descending (latest first)
            .limit(1)
            .build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    if (response.items().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(fromAttributeMap(response.items().get(0)));
  }

  /**
   * Find venues by geohash prefix (for nearby venue queries). Uses GSI3.
   *
   * @param geohashPrefix 6-character geohash prefix
   * @return List of venues in that geohash cell
   */
  public List<VenueLocation> findByGeohashPrefix(String geohashPrefix) {
    try {
      Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
      expressionAttributeValues.put(
          ":geohashPrefix", AttributeValue.builder().s(geohashPrefix).build());

      // GSI3 partition key is geohash_prefix (matches existing index structure)
      QueryRequest queryRequest =
          QueryRequest.builder()
              .tableName(tableName)
              .indexName("GSI3") // Geohash index
              .keyConditionExpression("geohash_prefix = :geohashPrefix")
              .expressionAttributeValues(expressionAttributeValues)
              .build();

      QueryResponse response = dynamoDbClient.query(queryRequest);

      System.out.println(
          "DEBUG: GSI3 query for geohash "
              + geohashPrefix
              + " returned "
              + response.items().size()
              + " items");

      List<VenueLocation> venues = new ArrayList<>();
      for (Map<String, AttributeValue> item : response.items()) {
        venues.add(fromAttributeMap(item));
      }

      return venues;
    } catch (Exception e) {
      // Log error but don't throw - return empty list instead
      System.err.println(
          "Error querying GSI3 for geohash " + geohashPrefix + ": " + e.getMessage());
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  /**
   * Find venues in multiple geohash cells (for nearby venue queries with 9 neighboring cells).
   *
   * @param geohashPrefixes List of geohash prefixes to query
   * @return List of all venues found in those cells
   */
  public List<VenueLocation> findByGeohashPrefixes(List<String> geohashPrefixes) {
    List<VenueLocation> allVenues = new ArrayList<>();

    // Query each geohash cell
    for (String geohashPrefix : geohashPrefixes) {
      allVenues.addAll(findByGeohashPrefix(geohashPrefix));
    }

    return allVenues;
  }

  /** Convert VenueLocation to DynamoDB AttributeValue map. */
  private Map<String, AttributeValue> toAttributeMap(VenueLocation venue) {
    Map<String, AttributeValue> item = new HashMap<>();

    // Use entity type prefix for single-table design
    // PK = "VENUE#venueId", SK = sk
    String pk = "VENUE#" + venue.getVenueId();
    item.put("pk", AttributeValue.builder().s(pk).build()); // pk is the PK field
    item.put("sk", AttributeValue.builder().s(venue.getCreationTime().toString()).build());

    item.put("venueId", AttributeValue.builder().s(venue.getVenueId()).build());
    item.put(
        "creationTime", AttributeValue.builder().s(venue.getCreationTime().toString()).build());

    if (venue.getName() != null) {
      item.put("name", AttributeValue.builder().s(venue.getName()).build());
    }
    if (venue.getLatitude() != null) {
      item.put("latitude", AttributeValue.builder().n(venue.getLatitude().toString()).build());
    }
    if (venue.getLongitude() != null) {
      item.put("longitude", AttributeValue.builder().n(venue.getLongitude().toString()).build());
    }
    if (venue.getAddress() != null) {
      item.put("address", AttributeValue.builder().s(venue.getAddress()).build());
    }
    if (venue.getCity() != null) {
      item.put("city", AttributeValue.builder().s(venue.getCity()).build());
    }
    if (venue.getCountry() != null) {
      item.put("country", AttributeValue.builder().s(venue.getCountry()).build());
    }
    if (venue.getGeohashPrefix() != null) {
      item.put("geohash_prefix", AttributeValue.builder().s(venue.getGeohashPrefix()).build());
      // Note: GSI3 uses geohash_prefix as partition key (not GSI3PK)
    }
    if (venue.getCreatedAt() != null) {
      item.put("createdAt", AttributeValue.builder().s(venue.getCreatedAt().toString()).build());
    }
    if (venue.getUpdatedAt() != null) {
      item.put("updatedAt", AttributeValue.builder().s(venue.getUpdatedAt().toString()).build());
    }

    return item;
  }

  /** Convert DynamoDB AttributeValue map to VenueLocation. */
  private VenueLocation fromAttributeMap(Map<String, AttributeValue> item) {
    VenueLocation venue = new VenueLocation();

    if (item.containsKey("venueId")) {
      venue.setVenueId(item.get("venueId").s());
    }
    if (item.containsKey("creationTime")) {
      venue.setCreationTime(Instant.parse(item.get("creationTime").s()));
    }
    if (item.containsKey("name")) {
      venue.setName(item.get("name").s());
    }
    if (item.containsKey("latitude")) {
      venue.setLatitude(Double.parseDouble(item.get("latitude").n()));
    }
    if (item.containsKey("longitude")) {
      venue.setLongitude(Double.parseDouble(item.get("longitude").n()));
    }
    if (item.containsKey("address")) {
      venue.setAddress(item.get("address").s());
    }
    if (item.containsKey("city")) {
      venue.setCity(item.get("city").s());
    }
    if (item.containsKey("country")) {
      venue.setCountry(item.get("country").s());
    }
    if (item.containsKey("geohash_prefix")) {
      venue.setGeohashPrefix(item.get("geohash_prefix").s());
    }
    // Read from sk (sort key) or createdAt (for backward compatibility)
    if (item.containsKey("sk")) {
      venue.setCreatedAt(Instant.parse(item.get("sk").s()));
    } else if (item.containsKey("createdAt")) {
      venue.setCreatedAt(Instant.parse(item.get("createdAt").s()));
    }
    if (item.containsKey("updatedAt")) {
      venue.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
    }

    return venue;
  }
}
