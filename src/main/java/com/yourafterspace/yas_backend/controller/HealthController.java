package com.yourafterspace.yas_backend.controller;

import com.yourafterspace.yas_backend.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check controller.
 *
 * <p>Provides health check endpoints for monitoring and load balancer health checks.
 */
@RestController
public class HealthController {

  /**
   * Simple health check endpoint.
   *
   * @return Health status response
   */
  @GetMapping("/health")
  public ResponseEntity<ApiResponse<Map<String, String>>> health() {
    return ResponseEntity.ok(ApiResponse.success("Service is healthy", Map.of("status", "OK")));
  }
}
