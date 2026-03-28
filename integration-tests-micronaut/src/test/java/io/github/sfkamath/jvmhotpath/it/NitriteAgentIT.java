package io.github.sfkamath.jvmhotpath.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sfkamath.jvmhotpath.ReportGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitriteAgentIT {

  @TempDir Path tempDir;

  @Test
  void testAgentInstrumentsNitriteLibrary() throws Exception {
    NitriteBuilder builder = Nitrite.builder();
    try (Nitrite db = builder.openOrCreate()) {
      NitriteCollection collection = db.getCollection("test");

      collection.createIndex(IndexOptions.indexOptions(IndexType.UNIQUE), "name");
      collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "age");
      collection.createIndex(IndexOptions.indexOptions(IndexType.UNIQUE), "name", "age");

      for (int i = 0; i < 100; i++) {
        collection.insert(Document.createDocument("name", "user" + i).put("age", i % 50));
      }

      for (int i = 0; i < 50; i++) {
        collection.find().toList();
      }
    }

    Path htmlReport = Path.of("target/site/jvm-hotpath/execution-report.html");
    ReportGenerator.generateHtmlReport(htmlReport.toString(), "", false);

    Path jsonReport = Path.of("target/site/jvm-hotpath/execution-report.json");
    assertTrue(Files.exists(jsonReport), "Report JSON should exist");

    String json = Files.readString(jsonReport);

    long compoundIndexCount = maxCountForFile(json, "CompoundIndex.java");
    long singleFieldIndexCount = maxCountForFile(json, "SingleFieldIndex.java");

    System.out.println("CompoundIndex max count: " + compoundIndexCount);
    System.out.println("SingleFieldIndex max count: " + singleFieldIndexCount);

    assertTrue(json.contains("CompoundIndex.java"), "CompoundIndex.java should be in the report");
    assertTrue(
        compoundIndexCount > 0,
        "CompoundIndex should have recorded counts, but was " + compoundIndexCount);

    assertTrue(
        json.contains("SingleFieldIndex.java"), "SingleFieldIndex.java should be in the report");
    assertTrue(
        singleFieldIndexCount > 0,
        "SingleFieldIndex should have recorded counts, but was " + singleFieldIndexCount);
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
}
