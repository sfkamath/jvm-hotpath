package io.github.sfkamath.jvmhotpath.it;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.sfkamath.jvmhotpath.ReportGenerator;
import io.github.sfkamath.jvmhotpath.sample.SampleApp;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  @Test
  void testAgentInstrumentsSpringBootApp() throws Exception {
    assumeTrue(hasJvmHotpathAgentAttached());

    // 1. Call the REST endpoint several times
    for (int i = 0; i < 50; i++) {
      String response =
          restTemplate.getForObject("http://localhost:" + port + "/hello", String.class);
      assertEquals("Hello from Spring Boot!", response);
    }

    // 2. Generate a fresh report immediately to avoid timing-based polling.
    Path htmlReport = Path.of("target/site/jvm-hotpath/execution-report.html");
    Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
    ReportGenerator.generateHtmlReport(htmlReport.toString(), sourceRoot.toString(), false);

    // 3. Parse and verify counts
    Path jsonReport = Path.of("target/site/jvm-hotpath/execution-report.json");
    assertTrue(Files.exists(jsonReport), "Report JSON should exist at " + jsonReport);
    String json = Files.readString(jsonReport);

    assertTrue(json.contains("GreetingService.java"), "GreetingService should be present.");
    long serviceMaxCount = maxCountForFile(json, "GreetingService.java");
    assertTrue(
        serviceMaxCount >= 50,
        "Expected GreetingService to reach 50+ executions. Max count was " + serviceMaxCount);
  }

  private static long maxCountForFile(String json, String fileName) {
    Pattern filePattern =
        Pattern.compile(
            "\"path\"\\s*:\\s*\"[^\"]*"
                + Pattern.quote(fileName)
                + "\".*?\"counts\"\\s*:\\s*\\{(.*?)\\}",
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

  private static boolean hasJvmHotpathAgentAttached() {
    return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .anyMatch(arg -> arg.startsWith("-javaagent:") && arg.contains("jvm-hotpath"));
  }
}
