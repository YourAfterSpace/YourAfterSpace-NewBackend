package com.yourafterspace.yas_backend.util;

import ch.hsr.geohash.GeoHash;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for geohashing operations. Used for location-based queries to find nearby venues
 * and experiences.
 */
public class GeohashUtil {

  private static final int GEOHASH_PRECISION = 6; // ~1.2km x 0.6km for NYC area

  /**
   * Calculate geohash for given latitude and longitude.
   *
   * @param latitude Latitude
   * @param longitude Longitude
   * @return Geohash string
   */
  public static String calculateGeohash(double latitude, double longitude) {
    GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, GEOHASH_PRECISION);
    return geoHash.toBase32();
  }

  /**
   * Get geohash prefix (first 6 characters) for storing in database.
   *
   * @param latitude Latitude
   * @param longitude Longitude
   * @return Geohash prefix (6 characters)
   */
  public static String getGeohashPrefix(double latitude, double longitude) {
    return calculateGeohash(latitude, longitude);
  }

  /**
   * Get the 9 neighboring geohash cells (3x3 grid) including the center cell. This is used for
   * finding nearby venues/experiences.
   *
   * @param latitude Center latitude
   * @param longitude Center longitude
   * @return List of 9 geohash prefixes (center + 8 neighbors)
   */
  public static List<String> getNeighboringGeohashes(double latitude, double longitude) {
    GeoHash center = GeoHash.withCharacterPrecision(latitude, longitude, GEOHASH_PRECISION);
    List<String> neighbors = new ArrayList<>();

    // Add center
    neighbors.add(center.toBase32());

    // Add 8 neighbors
    GeoHash[] adjacent = center.getAdjacent();
    for (GeoHash neighbor : adjacent) {
      neighbors.add(neighbor.toBase32());
    }

    return neighbors;
  }

  /**
   * Calculate distance between two points using Haversine formula.
   *
   * @param lat1 Latitude of first point
   * @param lon1 Longitude of first point
   * @param lat2 Latitude of second point
   * @param lon2 Longitude of second point
   * @return Distance in kilometers
   */
  public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
    final int R = 6371; // Radius of the earth in km

    double latDistance = Math.toRadians(lat2 - lat1);
    double lonDistance = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    double distance = R * c;

    return distance;
  }
}
