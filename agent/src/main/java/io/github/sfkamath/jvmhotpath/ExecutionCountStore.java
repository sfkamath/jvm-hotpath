package io.github.sfkamath.jvmhotpath;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe storage for execution counts. Each line in each class gets its own counter. */
public final class ExecutionCountStore {

  // Map: ClassName -> (LineNumber -> ExecutionCount)
  private static final Map<String, Map<Integer, AtomicLong>> counters = new ConcurrentHashMap<>();
  // Map: ClassName -> SourceChecksum
  private static final Map<String, String> checksums = new ConcurrentHashMap<>();

  /** Record the current checksum for a class. */
  public static void recordChecksum(String className, String checksum) {
    if (checksum != null) {
      checksums.put(className, checksum);
    }
  }

  /** Get the current checksum for a class. */
  public static String getChecksum(String className) {
    return checksums.get(className);
  }

  /** Increment the execution count for a specific line in a class. */
  public static void recordExecution(String className, int lineNumber) {
    counters
        .computeIfAbsent(className, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(lineNumber, k -> new AtomicLong(0))
        .incrementAndGet();
  }

  /** Get the execution count for a specific line. */
  public static long getCount(String className, int lineNumber) {
    Map<Integer, AtomicLong> classCounters = counters.get(className);
    if (classCounters == null) {
      return 0;
    }
    AtomicLong counter = classCounters.get(lineNumber);
    return counter == null ? 0 : counter.get();
  }

  /** Get all execution counts. */
  public static Map<String, Map<Integer, Long>> getAllCountersSnapshot() {
    Map<String, Map<Integer, Long>> snapshot = new ConcurrentHashMap<>();
    for (Map.Entry<String, Map<Integer, AtomicLong>> classEntry : counters.entrySet()) {
      Map<Integer, Long> lineCounts = new ConcurrentHashMap<>();
      for (Map.Entry<Integer, AtomicLong> lineEntry : classEntry.getValue().entrySet()) {
        lineCounts.put(lineEntry.getKey(), lineEntry.getValue().get());
      }
      snapshot.put(classEntry.getKey(), lineCounts);
    }
    return snapshot;
  }

  /** Clear all counters and checksums. */
  public static void reset() {
    counters.clear();
    checksums.clear();
  }

  /**
   * Seeds the store with existing counts, but ONLY if the checksums match.
   *
   * @param className The class to seed.
   * @param checksum The checksum from the saved report.
   * @param existingCounts The counts to add.
   * @return true if seeded, false if ignored due to checksum mismatch.
   */
  public static boolean seedCounts(
      String className, String checksum, Map<Integer, Long> existingCounts) {
    if (className == null || existingCounts == null || existingCounts.isEmpty()) {
      return false;
    }

    String currentChecksum = checksums.get(className);
    // If we have a current checksum and it doesn't match the seed checksum, it's drift!
    if (currentChecksum != null && !currentChecksum.equals(checksum)) {
      return false;
    }

    Map<Integer, AtomicLong> classCounters =
        counters.computeIfAbsent(className, k -> new ConcurrentHashMap<>());
    existingCounts.forEach(
        (line, count) ->
            classCounters.computeIfAbsent(line, k -> new AtomicLong(0)).addAndGet(count));

    return true;
  }

  private ExecutionCountStore() {}
}
