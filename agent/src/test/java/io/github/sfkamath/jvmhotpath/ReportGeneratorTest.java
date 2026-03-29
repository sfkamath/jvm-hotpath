package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportGeneratorTest {

  @BeforeEach
  void setUp() {
    ExecutionCountStore.reset();
    ReportGenerator.resetReportLocationLogForTests();
  }

  @Test
  void testCollectDataWithMultipleRoots() throws IOException {
    Path root1 = Files.createTempDirectory("root1");
    Path root2 = Files.createTempDirectory("root2");

    try {
      Path file1 = root1.resolve("com/example/File1.java");
      Files.createDirectories(file1.getParent());
      Files.writeString(file1, "package com.example; public class File1 {}");

      Path file2 = root2.resolve("org/test/File2.java");
      Files.createDirectories(file2.getParent());
      Files.writeString(file2, "package org.test; public class File2 {}");

      ExecutionCountStore.recordExecution("com.example.File1", 10);

      String sourcePath =
          root1.toAbsolutePath().toString()
              + File.pathSeparator
              + root2.toAbsolutePath().toString();
      List<ReportGenerator.FileData> data = ReportGenerator.collectData(sourcePath, false);

      assertEquals(2, data.size());

      ReportGenerator.FileData fd1 =
          data.stream()
              .filter(f -> "com/example/File1.java".equals(f.getPath()))
              .findFirst()
              .orElseThrow();
      assertEquals(1L, fd1.getCounts().get(10));
      assertTrue(
          fd1.getProject().startsWith("root1"),
          "Project name should start with root1, but was: " + fd1.getProject());

      ReportGenerator.FileData fd2 =
          data.stream()
              .filter(f -> "org/test/File2.java".equals(f.getPath()))
              .findFirst()
              .orElseThrow();
      assertTrue(fd2.getCounts().isEmpty());
      assertTrue(
          fd2.getProject().startsWith("root2"),
          "Project name should start with root2, but was: " + fd2.getProject());

    } finally {
      deleteRecursive(root1.toFile());
      deleteRecursive(root2.toFile());
    }
  }

  @Test
  void testAbsoluteNormalizationDeduplication() throws IOException {
    Path root = Files.createTempDirectory("dedupe");
    try {
      Path file = root.resolve("com/Main.java");
      Files.createDirectories(file.getParent());
      Files.writeString(file, "content");

      String path1 = root.toAbsolutePath().toString();
      String path2 = root.toAbsolutePath().toString() + "/./";

      List<ReportGenerator.FileData> data =
          ReportGenerator.collectData(path1 + File.pathSeparator + path2, false);
      assertEquals(1, data.size());
    } finally {
      deleteRecursive(root.toFile());
    }
  }

  @Test
  void testGenerateHtmlReport() throws IOException {
    Path outputDir = Files.createTempDirectory("output");
    Path sourceRoot = Files.createTempDirectory("source");
    try {
      Path javaFile = sourceRoot.resolve("Test.java");
      Files.writeString(javaFile, "public class Test {}");
      ExecutionCountStore.recordExecution("Test", 1);

      String reportPath = outputDir.resolve("report.html").toString();
      ReportGenerator.generateHtmlReport(reportPath, sourceRoot.toString(), true);

      assertTrue(Files.exists(outputDir.resolve("report.html")));
      assertTrue(Files.exists(outputDir.resolve("report.json")));
      assertTrue(Files.exists(outputDir.resolve("report.js")));
      assertTrue(Files.exists(outputDir.resolve("report-app.js")));

      String htmlContent = Files.readString(outputDir.resolve("report.html"));
      assertTrue(htmlContent.contains("Test.java"));
    } finally {
      deleteRecursive(outputDir.toFile());
      deleteRecursive(sourceRoot.toFile());
    }
  }

  @Test
  void testGroupingInnerClasses() throws IOException {
    Path root = Files.createTempDirectory("inner");
    try {
      Path file = root.resolve("com/Outer.java");
      Files.createDirectories(file.getParent());
      Files.writeString(file, "class Outer { class Inner {} }");

      ExecutionCountStore.recordExecution("com.Outer", 10);
      ExecutionCountStore.recordExecution("com.Outer$Inner", 20);

      List<ReportGenerator.FileData> data = ReportGenerator.collectData(root.toString(), false);
      assertEquals(1, data.size());
      assertEquals(1L, data.get(0).getCounts().get(10));
      assertEquals(1L, data.get(0).getCounts().get(20));
    } finally {
      deleteRecursive(root.toFile());
    }
  }

  @Test
  void testRegenerateReportVariations() throws Exception {
    Path tempDir = Files.createTempDirectory("hotpath-regen");
    Path jsonFile = tempDir.resolve("data.json");
    Path outputFile = tempDir.resolve("report.html");

    try {
      // 1. Array-based payload (old format or shorthand)
      Files.writeString(
          jsonFile,
          "[{\"path\":\"Old.java\", \"counts\":{\"10\":5}, \"content\":\"code\", \"project\":\"p\"}]");
      ReportGenerator.regenerateReport(jsonFile.toString(), outputFile.toString());
      assertTrue(Files.readString(outputFile).contains("Old.java"));

      // 2. Object-based payload without generatedAt
      Files.writeString(
          jsonFile,
          "{\"files\":[{\"path\":\"New.java\", \"counts\":{\"5\":1}, \"content\":\"more code\", \"project\":\"p2\"}]}");
      ReportGenerator.regenerateReport(jsonFile.toString(), outputFile.toString());
      assertTrue(Files.readString(outputFile).contains("New.java"));
    } finally {
      deleteRecursive(tempDir.toFile());
    }
  }

  @Test
  void testSourceRootParsingAndProjectDerivation() throws Exception {
    Path root = Files.createTempDirectory("project-root");
    Path src = root.resolve("src");
    Files.createDirectories(src);

    try {
      List<ReportGenerator.FileData> data = ReportGenerator.collectData(src.toString(), true);
      assertNotNull(data);

      // Test empty/invalid paths
      assertTrue(ReportGenerator.collectData(null, false).isEmpty());
      assertTrue(ReportGenerator.collectData("  ", false).isEmpty());
      assertTrue(ReportGenerator.collectData("/non/existent/path/at/all", false).isEmpty());
    } finally {
      deleteRecursive(root.toFile());
    }
  }

  @Test
  void testMergingWithInnerClasses() throws Exception {
    ExecutionCountStore.reset();
    ExecutionCountStore.recordExecution("com.app.Service", 10);
    ExecutionCountStore.recordExecution("com.app.Service$Inner", 20);

    // Should merge into Service.java
    List<ReportGenerator.FileData> data = ReportGenerator.collectData("", false);
    ReportGenerator.FileData serviceFile =
        data.stream()
            .filter(f -> "com/app/Service.java".equals(f.getPath()))
            .findFirst()
            .orElseThrow();

    assertEquals(1L, serviceFile.getCounts().get(10));
    assertEquals(1L, serviceFile.getCounts().get(20));
  }

  @Test
  void testFallbackProjectDerivation() throws Exception {
    Path root = Files.createTempDirectory("my-project");
    // Create something that is NOT 'src' or 'target' to trigger fallback logic
    Path other = root.resolve("other");
    Files.createDirectories(other);
    Path java = other.resolve("App.java");
    Files.writeString(java, "public class App {}");

    List<ReportGenerator.FileData> data = ReportGenerator.collectData(other.toString(), false);
    assertFalse(data.isEmpty());
    // derivator should fall back to the last segment of the path if src/target not found
    assertEquals("other", data.get(0).getProject());

    deleteRecursive(root.toFile());
  }

  @Test
  void testCollectDataWithSourceArchive() throws Exception {
    Path tempDir = Files.createTempDirectory("archive-root");
    Path sourceArchive = tempDir.resolve("shared-library-sources.jar");
    try {
      writeSourceArchive(
          sourceArchive, "com/example/shared/SharedService.java", "class SharedService {}");

      ExecutionCountStore.recordExecution("com.example.shared.SharedService", 42);

      List<ReportGenerator.FileData> data =
          ReportGenerator.collectData(sourceArchive.toString(), false);

      ReportGenerator.FileData sharedFile =
          data.stream()
              .filter(f -> "com/example/shared/SharedService.java".equals(f.getPath()))
              .findFirst()
              .orElseThrow();

      assertTrue(sharedFile.getContent().contains("SharedService"));
      assertEquals(1L, sharedFile.getCounts().get(42));
      assertEquals("shared-library", sharedFile.getProject());
    } finally {
      deleteRecursive(tempDir.toFile());
    }
  }

  @Test
  void testReportLocationLogUsesFileUriAndPrintsOnce() throws IOException {
    Path outputDir = Files.createTempDirectory("report-log");
    Path sourceRoot = Files.createTempDirectory("source-root");
    Path javaFile = sourceRoot.resolve("Sample.java");
    Files.writeString(javaFile, "public class Sample {}", StandardCharsets.UTF_8);

    Logger logger = Logger.getLogger(ReportGenerator.class.getName());
    Level previousLevel = logger.getLevel();
    List<String> messages = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            if (record != null && record.getMessage() != null) {
              messages.add(record.getMessage());
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };

    try {
      logger.setLevel(Level.INFO);
      logger.addHandler(handler);

      String reportPath = outputDir.resolve("report.html").toString();
      ReportGenerator.generateHtmlReport(reportPath, sourceRoot.toString(), false);
      ReportGenerator.generateHtmlReport(reportPath, sourceRoot.toString(), false);

      long reportWrittenMessages =
          messages.stream().filter(m -> m.startsWith("Report written to: ")).count();
      assertEquals(1, reportWrittenMessages);

      String reportUri =
          messages.stream()
              .filter(m -> m.startsWith("Report written to: "))
              .findFirst()
              .orElseThrow()
              .substring("Report written to: ".length());

      assertTrue(reportUri.startsWith("file:///"), "Expected a file URI: " + reportUri);
      assertFalse(reportUri.startsWith("file:////"), "URI has an extra slash: " + reportUri);
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(previousLevel);
      deleteRecursive(outputDir.toFile());
      deleteRecursive(sourceRoot.toFile());
    }
  }

  @Test
  void testChecksumAndRehydration() throws IOException {
    Path tempDir = Files.createTempDirectory("rehydrate-test");
    Path sourceRoot = tempDir.resolve("src");
    Files.createDirectories(sourceRoot);
    Path javaFile = sourceRoot.resolve("Service.java");
    String content = "public class Service { public void run() {} }";
    Files.writeString(javaFile, content);

    try {
      // 1. Generate a report to create valid rehydration data
      ExecutionCountStore.recordExecution("Service", 10);
      String reportPath = tempDir.resolve("report.html").toString();
      String jsonPath = tempDir.resolve("report.json").toString();
      ReportGenerator.generateHtmlReport(reportPath, sourceRoot.toString(), false);

      // Verify checksum was generated
      List<ReportGenerator.FileData> data =
          ReportGenerator.collectData(sourceRoot.toString(), false);
      String checksum = data.get(0).getChecksum();
      assertNotNull(checksum);
      assertNotEquals("0", checksum);

      // 2. Clear store and rehydrate — no pre-populated checksums (real append mode scenario)
      ExecutionCountStore.reset();
      ReportGenerator.rehydrate(jsonPath, sourceRoot.toString());
      assertEquals(1L, ExecutionCountStore.getCount("Service", 10), "Should rehydrate counts");

      // 3. Test rehydration WITH DRIFT (change source content, no pre-populated checksums)
      Files.writeString(javaFile, content + "// drift!");
      ExecutionCountStore.reset();

      ReportGenerator.rehydrate(jsonPath, sourceRoot.toString());
      assertEquals(
          0L, ExecutionCountStore.getCount("Service", 10), "Should ignore counts due to drift");

    } finally {
      deleteRecursive(tempDir.toFile());
    }
  }

  @Test
  void testRehydrateWithDriftLogging() throws IOException {
    Path tempDir = Files.createTempDirectory("rehydrate-log-test");
    Path sourceRoot = tempDir.resolve("src");
    Files.createDirectories(sourceRoot);
    // Write a source file whose checksum will differ from the one saved in the report
    Files.writeString(sourceRoot.resolve("Drifted.java"), "public class Drifted { }");
    Path jsonFile = tempDir.resolve("report.json");
    Files.writeString(
        jsonFile,
        "{\"files\":[{\"path\":\"Drifted.java\", \"counts\":{\"1\":10}, \"checksum\":\"OLD\"}]}");

    Logger logger = Logger.getLogger(ReportGenerator.class.getName());
    List<String> warnings = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            if (record.getLevel() == Level.WARNING) {
              warnings.add(record.getMessage());
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };

    try {
      logger.addHandler(handler);
      ExecutionCountStore.reset();

      ReportGenerator.rehydrate(jsonFile.toString(), sourceRoot.toString());

      assertTrue(
          warnings.stream().anyMatch(m -> m.contains("Source drift detected for Drifted")),
          "Expected drift warning log, but got: " + warnings);
    } finally {
      logger.removeHandler(handler);
      deleteRecursive(tempDir.toFile());
    }
  }

  @Test
  void testRehydrateFromNonExistentFile() {
    // Should just return silently
    ReportGenerator.rehydrate("non-existent.json", "");
  }

  /**
   * Regression test: two source roots each contain a file named SpatialIndex.java but under
   * different packages. Execution counts for org.dizitart.no2.index.SpatialIndex must be matched to
   * the nitrite source file, not to the micronaut annotation that shares the same simple filename.
   */
  @Test
  void testSameFilenameInDifferentPackagesIsNotConfused() throws IOException {
    Path micronautRoot = Files.createTempDirectory("micronaut-src");
    Path nitriteRoot = Files.createTempDirectory("nitrite-src");

    try {
      // Micronaut: io/micronaut/data/annotation/SpatialIndex.java
      Path micronautFile =
          micronautRoot.resolve("io/micronaut/data/annotation/SpatialIndex.java");
      Files.createDirectories(micronautFile.getParent());
      Files.writeString(micronautFile, "package io.micronaut.data.annotation; @interface SpatialIndex {}");

      // Nitrite: org/dizitart/no2/index/SpatialIndex.java
      Path nitriteFile = nitriteRoot.resolve("org/dizitart/no2/index/SpatialIndex.java");
      Files.createDirectories(nitriteFile.getParent());
      Files.writeString(nitriteFile, "package org.dizitart.no2.index; class SpatialIndex {}");

      ExecutionCountStore.recordExecution("org.dizitart.no2.index.SpatialIndex", 42);

      // Micronaut source is listed first — the old filename-only fallback would return it wrongly
      String sourcePath =
          micronautRoot.toAbsolutePath()
              + File.pathSeparator
              + nitriteRoot.toAbsolutePath();
      List<ReportGenerator.FileData> data = ReportGenerator.collectData(sourcePath, false);

      ReportGenerator.FileData nitriteData =
          data.stream()
              .filter(f -> "org/dizitart/no2/index/SpatialIndex.java".equals(f.getPath()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Nitrite SpatialIndex.java not found in report"));

      assertEquals(1L, nitriteData.getCounts().get(42), "Counts must be on the nitrite SpatialIndex");
      assertTrue(
          nitriteData.getContent().contains("org.dizitart.no2.index"),
          "Content should be from nitrite, not micronaut, but was: " + nitriteData.getContent());
    } finally {
      deleteRecursive(micronautRoot.toFile());
      deleteRecursive(nitriteRoot.toFile());
    }
  }

  private void deleteRecursive(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursive(child);
      }
    }
    file.delete();
  }

  private void writeSourceArchive(Path archive, String entryPath, String content)
      throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      ZipEntry entry = new ZipEntry(entryPath);
      zip.putNextEntry(entry);
      zip.write(content.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }
}
