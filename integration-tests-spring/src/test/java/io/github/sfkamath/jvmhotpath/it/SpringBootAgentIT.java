package io.github.sfkamath.jvmhotpath.it;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sfkamath.jvmhotpath.sample.SampleApp;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SampleApp.class,
    properties = "spring.main.banner-mode=off")
class SpringBootAgentIT {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testAgentInstrumentsSpringBootApp() throws Exception {
    // 1. Call the REST endpoint several times
    for (int i = 0; i < 50; i++) {
      String response =
          restTemplate.getForObject("http://localhost:" + port + "/hello", String.class);
      assertEquals("Hello from Spring Boot!", response);
    }

    // 2. Wait for the auto-flush to write the report with the expected counts
    Path jsonReport = Path.of("target/execution-report.json");
    boolean thresholdReached = false;
    String debugReport = "";
    for (int i = 0; i < 40; i++) {
      if (Files.exists(jsonReport)) {
        JsonNode root = mapper.readTree(jsonReport.toFile());
        JsonNode files = root.get("files");
        if (files != null) {
          for (JsonNode file : files) {
            if (file.get("path").asText().contains("GreetingService.java")) {
              JsonNode counts = file.get("counts");
              for (JsonNode val : counts) {
                if (val.asInt() >= 50) {
                  thresholdReached = true;
                  break;
                }
              }
            }
          }
        }
        if (thresholdReached) {
          break;
        }
        debugReport = root.toString();
      }
      Thread.sleep(1000);
    }

    assertTrue(
        thresholdReached,
        "Report should have reached 50+ executions for GreetingService. Last report: "
            + debugReport);

    // 3. Parse and verify counts
    JsonNode root = mapper.readTree(jsonReport.toFile());
    JsonNode files = root.get("files");
    assertNotNull(files);

    boolean serviceFound = false;
    StringBuilder debugInfo = new StringBuilder();
    for (JsonNode file : files) {
      String path = file.get("path").asText();
      JsonNode counts = file.get("counts");
      debugInfo.append("\nFile: ").append(path).append(" Counts: ").append(counts);

      if (path.contains("GreetingService.java")) {
        serviceFound = true;
        final boolean[] wrapper = new boolean[]{false};
        counts
            .properties()
            .forEach(
                entry -> {
                  int val = entry.getValue().asInt();
                  if (val >= 5) {
                    wrapper[0] = true;
                  }
                });
        assertTrue(
            wrapper[0],
            "GreetingService should have lines with at least 5 executions. Found: " + debugInfo);
      }
    }
    assertTrue(serviceFound, "GreetingService should be present in the report.");
  }
}
