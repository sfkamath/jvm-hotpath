package io.github.sfkamath.jvmhotpath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utility to scan source paths and identify available source files. */
final class SourcePathScanner {

  /**
   * Scans the given source path and returns a set of available Java source files (e.g.,
   * "io/github/sfkamath/Foo.java").
   */
  static Set<String> scan(String sourcePath) {
    Set<String> availableSources = new HashSet<>();
    if (sourcePath == null || sourcePath.trim().isEmpty()) {
      return availableSources;
    }

    for (String s : sourcePath.split(File.pathSeparator)) {
      String trimmed = s.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      Path path = Path.of(trimmed).toAbsolutePath().normalize();
      if (!Files.exists(path)) {
        continue;
      }

      try {
        if (Files.isDirectory(path)) {
          scanDirectory(path, path, availableSources);
        } else if (isArchive(path)) {
          scanArchive(path, availableSources);
        }
      } catch (IOException ignored) {
        // Skip broken roots
      }
    }
    return availableSources;
  }

  private static void scanDirectory(Path root, Path current, Set<String> found) throws IOException {
    try (var stream = Files.list(current)) {
      stream.forEach(
          p -> {
            if (Files.isDirectory(p)) {
              try {
                scanDirectory(root, p, found);
              } catch (IOException ignored) {
              }
            } else if (p.toString().endsWith(".java")) {
              found.add(root.relativize(p).toString().replace('\\', '/'));
            }
          });
    }
  }

  private static void scanArchive(Path archive, Set<String> found) throws IOException {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory() && entry.getName().endsWith(".java")) {
          String name = entry.getName().replace('\\', '/');
          while (name.startsWith("/")) {
            name = name.substring(1);
          }
          found.add(name);
        }
      }
    }
  }

  private static boolean isArchive(Path path) {
    Path fileName = path.getFileName();
    if (fileName == null) {
      return false;
    }
    String name = fileName.toString().toLowerCase();
    return name.endsWith(".jar") || name.endsWith(".zip");
  }

  private SourcePathScanner() {}
}
