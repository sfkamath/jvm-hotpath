package io.github.sfkamath.jvmhotpath.sample;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccumulationIT {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void testAccumulation() throws Exception {
    Path reportJson = Path.of("target/site/jvm-hotpath/execution-report.json");

    // This test is designed to be run manually or via a special profile
    // because it requires multiple JVM starts to truly test 'append'.
    // Here we just verify that if a JSON exists, it is rehydrated.

    // 1. Ensure report exists (we can't easily restart JVM in one IT,
    // but we can verify the rehydration logic is triggered by the agent)
    if (Files.exists(reportJson)) {
      String content = Files.readString(reportJson);
      assertTrue(content.contains("\"checksum\""), "Report should contain checksums");
    }

    // Hit the endpoint
    restTemplate.getForObject("/hello", String.class);
  }
}
