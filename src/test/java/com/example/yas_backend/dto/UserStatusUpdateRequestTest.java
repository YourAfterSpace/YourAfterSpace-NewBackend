package com.example.yas_backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourafterspace.yas_backend.dto.UserStatusUpdateRequest;
import com.yourafterspace.yas_backend.model.UserProfile.UserStatus;
import org.junit.jupiter.api.Test;

class UserStatusUpdateRequestTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testJsonDeserialization() throws Exception {
    // Test JSON string with "DELETED" status
    String json = "{\"status\": \"DELETED\", \"reason\": \"Admin action - policy violation\"}";

    // Deserialize JSON to object
    UserStatusUpdateRequest request = objectMapper.readValue(json, UserStatusUpdateRequest.class);

    // Verify deserialization
    assertThat(request.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(request.getReason()).isEqualTo("Admin action - policy violation");
  }

  @Test
  void testJsonSerialization() throws Exception {
    // Create request object
    UserStatusUpdateRequest request =
        new UserStatusUpdateRequest(UserStatus.DELETED, "Test reason");

    // Serialize to JSON
    String json = objectMapper.writeValueAsString(request);

    // Verify JSON contains expected values
    assertThat(json).contains("\"status\":\"DELETED\"");
    assertThat(json).contains("\"reason\":\"Test reason\"");
  }

  @Test
  void testActiveStatus() throws Exception {
    // Test JSON string with "ACTIVE" status
    String json = "{\"status\": \"ACTIVE\", \"reason\": \"User reactivation\"}";

    // Deserialize JSON to object
    UserStatusUpdateRequest request = objectMapper.readValue(json, UserStatusUpdateRequest.class);

    // Verify deserialization
    assertThat(request.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(request.getReason()).isEqualTo("User reactivation");
  }
}
