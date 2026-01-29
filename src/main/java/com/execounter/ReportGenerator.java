package com.execounter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/** Generates HTML report showing execution counts per line using a Vue.js template. */
public final class ReportGenerator {

  private static final ObjectMapper mapper = new ObjectMapper();

  /** Generates the report from current memory state. */
  public static void generateHtmlReport(String outputPath, String sourcePath) throws IOException {
    List<FileData> data = collectData(sourcePath);
    ReportPayload payload = new ReportPayload(System.currentTimeMillis(), data);
    String jsonData = mapper.writeValueAsString(payload);

    ReportPaths paths = resolveReportPaths(outputPath);

    // 1. Save pure JSON
    Files.createDirectories(paths.outputDir);
    Files.writeString(paths.jsonPath, jsonData);

    // 2. Save JSONP (for serverless live updates)
    String jsonpContent = "window.loadExecutionData && window.loadExecutionData(" + jsonData + ");";
    Files.writeString(paths.jsonpPath, jsonpContent);

    // 3. Render HTML (embedded data for initial load)
    renderReport(payload, paths);
  }

  /** Regenerates the report from a saved JSON data file. */
  public static void regenerateReport(String jsonPath, String outputPath) throws IOException {
    ReportPayload payload = readPayload(jsonPath);
    renderReport(payload, resolveReportPaths(outputPath));
  }

  private static List<FileData> collectData(String sourcePath) throws IOException {
    Map<String, Map<Integer, Long>> allCounters = ExecutionCountStore.getAllCountersSnapshot();
    List<FileData> fileDataList = new ArrayList<>();

    if (allCounters.isEmpty()) {
      return fileDataList;
    }

    Map<String, Map<Integer, Long>> groupedCounters = new HashMap<>();
    List<SourceRoot> roots = parseSourceRoots(sourcePath);

    for (Map.Entry<String, Map<Integer, Long>> classEntry : allCounters.entrySet()) {
      String className = classEntry.getKey();
      String topLevelClass =
          className.contains("$") ? className.substring(0, className.indexOf('$')) : className;

      Map<Integer, Long> targetMap =
          groupedCounters.computeIfAbsent(topLevelClass, k -> new HashMap<>());
      for (Map.Entry<Integer, Long> lineEntry : classEntry.getValue().entrySet()) {
        targetMap.merge(lineEntry.getKey(), lineEntry.getValue(), Long::sum);
      }
    }

    for (Map.Entry<String, Map<Integer, Long>> entry : groupedCounters.entrySet()) {
      String className = entry.getKey();
      Map<Integer, Long> counts = entry.getValue();

      String relativePath = className.replace('.', '/') + ".java";
      SourceFile sourceFile = findSourceContent(roots, className, relativePath);
      String content = sourceFile.content();
      String project = sourceFile.project();

      fileDataList.add(new FileData(relativePath, counts, content, project));
    }

    fileDataList.sort(Comparator.comparing(ReportGenerator.FileData::getPath));
    return fileDataList;
  }

  private static void renderReport(ReportPayload payload, ReportPaths paths) throws IOException {
    String template = loadTemplate();
    if (template == null) {
      System.err.println("Could not load report template.");
      return;
    }

    String jsonData = mapper.writeValueAsString(payload);

    // Inject data and report file names
    String finalHtml =
        template
            .replace("/*DATA_MARKER*/ []", jsonData)
            .replace("/*GENERATED_AT*/ 0", Long.toString(payload.generatedAt))
            .replace("/*JSON_FILE*/", paths.jsonFileName)
            .replace("/*JSONP_FILE*/", paths.jsonpFileName);

    Files.writeString(paths.htmlPath, finalHtml);
    System.out.println("Report written to: " + paths.htmlPath);

    copyResource(paths.outputDir, "/com/execounter/report-app.js", "report-app.js");
  }

  private static SourceFile findSourceContent(
      List<SourceRoot> roots, String className, String relativePath) {
    if (roots.isEmpty()) {
      return SourceFile.missing(relativePath, fallbackProject(roots, relativePath));
    }

    String filename = simpleClassName(className) + ".java";
    for (SourceRoot root : roots) {
      try {
        Path candidate = root.path().resolve(relativePath);
        if (Files.exists(candidate)) {
          return new SourceFile(Files.readString(candidate), root.project());
        }

        try (Stream<Path> walker = Files.walk(root.path())) {
          Optional<Path> found =
              walker.filter(p -> p.getFileName().toString().equals(filename)).findFirst();
          if (found.isPresent()) {
            Path located = found.orElseThrow();
            return new SourceFile(Files.readString(located), root.project());
          }
        }
      } catch (IOException ignored) {
        // Skip broken roots and continue checking the remaining ones.
      }
    }

    return SourceFile.missing(relativePath, fallbackProject(roots, relativePath));
  }

  private static List<SourceRoot> parseSourceRoots(String sourcePath) {
    if (sourcePath == null || sourcePath.trim().isEmpty()) {
      return List.of();
    }

    return Arrays.stream(sourcePath.split(File.pathSeparator))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Path::of)
        .filter(Files::exists)
        .filter(Files::isDirectory)
        .map(root -> new SourceRoot(root, deriveProjectName(root)))
        .toList();
  }

  private static String deriveProjectName(Path root) {
    if (root == null) {
      return "unknown";
    }
    String normalized = root.toString().replace('\\', '/');
    String[] segments = normalized.split("/");
    for (int i = 0; i < segments.length; i++) {
      if ("src".equals(segments[i]) && i > 0) {
        return segments[i - 1];
      }
    }
    for (int i = 0; i < segments.length; i++) {
      if ("target".equals(segments[i]) && i > 0) {
        return segments[i - 1];
      }
    }
    for (int i = segments.length - 1; i >= 0; i--) {
      if (!segments[i].isBlank()) {
        return segments[i];
      }
    }
    return "unknown";
  }

  private static String fallbackProject(List<SourceRoot> roots, String relativePath) {
    if (!roots.isEmpty()) {
      return roots.get(0).project();
    }
    return "unknown";
  }

  private static String loadTemplate() throws IOException {
    try (InputStream is = ReportGenerator.class.getResourceAsStream("report-template.html")) {
      if (is == null) {
        return null;
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void copyResource(Path targetDir, String resourceName, String fileName)
      throws IOException {
    try (InputStream is = ReportGenerator.class.getResourceAsStream(resourceName)) {
      if (is == null) {
        System.err.println("Missing resource: " + resourceName);
        return;
      }
      Path dest = targetDir.resolve(fileName);
      Files.createDirectories(targetDir);
      Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static ReportPayload readPayload(String jsonPath) throws IOException {
    String raw = Files.readString(Path.of(jsonPath));
    var node = mapper.readTree(raw);
    if (node.isArray()) {
      List<FileData> files = mapper.convertValue(node, new TypeReference<List<FileData>>() {});
      return new ReportPayload(System.currentTimeMillis(), files);
    }
    var filesNode = node.get("files");
    List<FileData> files =
        filesNode != null && filesNode.isArray()
            ? mapper.convertValue(filesNode, new TypeReference<List<FileData>>() {})
            : List.of();
    long generatedAt = node.has("generatedAt") ? node.get("generatedAt").asLong() : 0L;
    if (generatedAt <= 0L) {
      generatedAt = System.currentTimeMillis();
    }
    return new ReportPayload(generatedAt, files);
  }

  private static ReportPaths resolveReportPaths(String outputPath) {
    String safeOutput =
        outputPath == null || outputPath.trim().isEmpty() ? "execution-report.html" : outputPath;
    Path htmlPath = Path.of(safeOutput);
    Path dir = htmlPath.getParent();
    Path fileNamePath = htmlPath.getFileName();
    String fileName = fileNamePath == null ? "execution-report.html" : fileNamePath.toString();
    String baseName =
        fileName.endsWith(".html") ? fileName.substring(0, fileName.length() - 5) : fileName;
    String jsonFileName = baseName + ".json";
    String jsonpFileName = baseName + ".js";
    Path outputDir = dir == null ? Path.of(".") : dir;
    Path jsonPath = outputDir.resolve(jsonFileName);
    Path jsonpPath = outputDir.resolve(jsonpFileName);
    return new ReportPaths(htmlPath, outputDir, jsonPath, jsonpPath, jsonFileName, jsonpFileName);
  }

  private static String simpleClassName(String className) {
    if (className == null || className.isEmpty()) {
      return "Unknown";
    }
    int lastDot = className.lastIndexOf('.');
    if (lastDot >= 0 && lastDot + 1 < className.length()) {
      return className.substring(lastDot + 1);
    }
    return className;
  }

  private static final class SourceRoot {
    private final Path path;
    private final String project;

    private SourceRoot(Path path, String project) {
      this.path = path;
      this.project = project;
    }

    private Path path() {
      return path;
    }

    private String project() {
      return project;
    }
  }

  private static final class SourceFile {
    private final String content;
    private final String project;

    private SourceFile(String content, String project) {
      this.content = content;
      this.project = project;
    }

    private static SourceFile missing(String relativePath, String project) {
      return new SourceFile("// Source file not found: " + relativePath, project);
    }

    private String content() {
      return content;
    }

    private String project() {
      return project;
    }
  }

  private static final class ReportPaths {
    private final Path htmlPath;
    private final Path outputDir;
    private final Path jsonPath;
    private final Path jsonpPath;
    private final String jsonFileName;
    private final String jsonpFileName;

    private ReportPaths(
        Path htmlPath,
        Path outputDir,
        Path jsonPath,
        Path jsonpPath,
        String jsonFileName,
        String jsonpFileName) {
      this.htmlPath = htmlPath;
      this.outputDir = outputDir;
      this.jsonPath = jsonPath;
      this.jsonpPath = jsonpPath;
      this.jsonFileName = jsonFileName;
      this.jsonpFileName = jsonpFileName;
    }
  }

  public static class FileData {
    private String path;
    private Map<Integer, Long> counts;
    private String content;
    private String project;

    public FileData() {}

    public FileData(String path, Map<Integer, Long> counts, String content, String project) {
      this.path = path;
      this.counts = counts == null ? new HashMap<>() : new HashMap<>(counts);
      this.content = content;
      this.project = project == null || project.isBlank() ? "unknown" : project;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public Map<Integer, Long> getCounts() {
      return counts == null ? Map.of() : Collections.unmodifiableMap(counts);
    }

    public void setCounts(Map<Integer, Long> counts) {
      this.counts = counts == null ? new HashMap<>() : new HashMap<>(counts);
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }

    public String getProject() {
      return project;
    }

    public void setProject(String project) {
      this.project = project == null || project.isBlank() ? "unknown" : project;
    }
  }

  public static final class ReportPayload {
    public final long generatedAt;
    public final List<FileData> files;

    public ReportPayload(long generatedAt, List<FileData> files) {
      this.generatedAt = generatedAt;
      this.files = files;
    }
  }

  private ReportGenerator() {}
}
