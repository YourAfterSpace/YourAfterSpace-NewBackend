package com.example.yas_backend;

import com.example.yas_backend.config.TestAwsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = com.yourafterspace.yas_backend.YasBackendApplication.class,
    properties = {"spring.profiles.active=test"})
@Import(TestAwsConfiguration.class)
class YasBackendApplicationTests {

  @Test
  void contextLoads() {}
}
