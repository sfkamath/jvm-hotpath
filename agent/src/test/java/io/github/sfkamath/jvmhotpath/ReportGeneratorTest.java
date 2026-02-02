package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportGeneratorTest {

  @BeforeEach
  void setUp() {
    ExecutionCountStore.reset();
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

  private void deleteRecursive(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursive(child);
      }
    }
    file.delete();
  }
}
