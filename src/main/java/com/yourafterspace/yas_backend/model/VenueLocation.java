package com.yourafterspace.yas_backend.model;

import java.time.Instant;

/**
 * VenueLocation entity stored in DynamoDB.
 *
 * <p>PK: venueId, SK: creationTime GSI1-PK: latitude, GSI1-SK: longitude GSI2-PK: longitude,
 * GSI2-SK: latitude geohash_prefix: GSI3-PK (for nearby searches)
 */
public class VenueLocation {

  private String venueId; // PK
  private Instant creationTime; // SK
  private String name;
  private Double latitude;
  private Double longitude;
  private String address;
  private String city;
  private String country;
  private String geohashPrefix; // First 6 characters of geohash for GSI3
  private Instant createdAt;
  private Instant updatedAt;

  public VenueLocation() {
    this.creationTime = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public VenueLocation(String venueId) {
    this();
    this.venueId = venueId;
  }

  public String getVenueId() {
    return venueId;
  }

  public void setVenueId(String venueId) {
    this.venueId = venueId;
  }

  public Instant getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(Instant creationTime) {
    this.creationTime = creationTime;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getGeohashPrefix() {
    return geohashPrefix;
  }

  public void setGeohashPrefix(String geohashPrefix) {
    this.geohashPrefix = geohashPrefix;
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
