package com.example.yas_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.yas_backend.config.TestAwsConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = com.yourafterspace.yas_backend.YasBackendApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=test"})
@Import(TestAwsConfiguration.class)
class HealthControllerTest {

  @LocalServerPort private int port;

  @Test
  void healthEndpointReturnsOkAndHasRequestIdHeader() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/health"))
            .GET()
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"status\":\"OK\""));
    assertTrue(response.body().contains("\"success\":true"));
    assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
  }
}
