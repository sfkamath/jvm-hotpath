package io.github.sfkamath.jvmhotpath.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@MicronautTest
class MicronautAgentIT {

  @Inject EmbeddedServer embeddedServer;

  @Inject
  @Client("/")
  HttpClient client;

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testAgentInstrumentsMicronautApp() throws Exception {
    // 1. Call the REST endpoint several times
    for (int i = 0; i < 50; i++) {
      String response = client.toBlocking().retrieve("/hello");
      assertEquals("Hello from Micronaut!", response);
    }

    // 2. Wait for the auto-flush to write the report with the expected counts
    // We use the same target directory as SpringBootAgentIT, but maybe we should ensure they don't
    // overlap if run in parallel.
    // For now, Failsafe runs them sequentially in the same JVM fork or different forks.
    Path jsonReport = Path.of("target/execution-report.json");
    boolean thresholdReached = false;

    for (int i = 0; i < 40; i++) {
      if (Files.exists(jsonReport)) {
        JsonNode root = mapper.readTree(jsonReport.toFile());
        JsonNode files = root.get("files");
        if (files != null) {
          for (JsonNode file : files) {
            if (file.get("path").asText().contains("MicronautGreetingService.java")) {
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
      }
      Thread.sleep(1000);
    }

    assertTrue(
        thresholdReached,
        "Report should have reached 50+ executions for MicronautGreetingService.");

    // 3. Verify specifically the controller hit
    JsonNode root = mapper.readTree(jsonReport.toFile());
    boolean controllerFound = false;
    for (JsonNode file : root.get("files")) {
      if (file.get("path").asText().contains("MicronautGreetingController.java")) {
        controllerFound = true;
        assertTrue(file.get("counts").size() > 0, "Controller should have recorded counts");
      }
    }
    assertTrue(controllerFound, "MicronautGreetingController should be in the report");
  }
}
