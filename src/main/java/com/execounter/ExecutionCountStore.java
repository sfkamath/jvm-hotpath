package com.execounter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe storage for execution counts. Each line in each class gets its own counter. */
public final class ExecutionCountStore {

  // Map: ClassName -> (LineNumber -> ExecutionCount)
  private static final Map<String, Map<Integer, AtomicLong>> counters = new ConcurrentHashMap<>();

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

  /** Clear all counters. */
  public static void reset() {
    counters.clear();
  }

  private ExecutionCountStore() {}
}
