package io.github.sfkamath.jvmhotpath.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sfkamath.jvmhotpath.ReportGenerator;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@MicronautTest
class MicronautAgentIT {

  @Inject EmbeddedServer embeddedServer;

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void testAgentInstrumentsMicronautApp() throws Exception {
    // 1. Call the REST endpoint several times
    for (int i = 0; i < 50; i++) {
      String response = client.toBlocking().retrieve("/hello");
      assertEquals("Hello from Micronaut!", response);
    }

    // 2. Generate a fresh report immediately to avoid timing-based polling.
    Path htmlReport = Path.of("target/site/jvm-hotpath/execution-report.html");
    Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
    ReportGenerator.generateHtmlReport(htmlReport.toString(), sourceRoot.toString(), false);

    // 3. Parse and verify counts.
    Path jsonReport = Path.of("target/site/jvm-hotpath/execution-report.json");
    assertTrue(Files.exists(jsonReport), "Report JSON should exist at " + jsonReport);

    String json = Files.readString(jsonReport);
    long serviceMaxCount = maxCountForFile(json, "MicronautGreetingService.java");
    assertTrue(json.contains("MicronautGreetingService.java"), "Service file should be present.");
    assertTrue(
        serviceMaxCount >= 50,
        "Report should have reached 50+ executions for MicronautGreetingService. Max count was "
            + serviceMaxCount);

    // 4. Verify specifically the controller hit.
    long controllerMaxCount = maxCountForFile(json, "MicronautGreetingController.java");
    assertTrue(
        json.contains("MicronautGreetingController.java"),
        "MicronautGreetingController should be in the report");
    assertTrue(
        controllerMaxCount > 0,
        "Controller should have recorded counts. Max count was " + controllerMaxCount);
  }

  private static long maxCountForFile(String json, String fileName) {
    Pattern filePattern =
        Pattern.compile(
            "\"path\"\\s*:\\s*\"[^\"]*" + Pattern.quote(fileName) + "\".*?\"counts\"\\s*:\\s*\\{(.*?)\\}",
            Pattern.DOTALL);
    Matcher fileMatcher = filePattern.matcher(json);
    long max = -1;
    while (fileMatcher.find()) {
      String countsBody = fileMatcher.group(1);
      Matcher valueMatcher = Pattern.compile(":\\s*(\\d+)").matcher(countsBody);
      while (valueMatcher.find()) {
        long value = Long.parseLong(valueMatcher.group(1));
        if (value > max) {
          max = value;
        }
      }
    }
    return max;
  }
}
