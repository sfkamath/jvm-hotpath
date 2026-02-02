package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionCountStoreTest {

  @BeforeEach
  void setUp() {
    ExecutionCountStore.reset();
  }

  @Test
  void testRecordAndGetCount() {
    ExecutionCountStore.recordExecution("com.example.Test", 10);
    ExecutionCountStore.recordExecution("com.example.Test", 10);
    ExecutionCountStore.recordExecution("com.example.Test", 20);

    assertEquals(2, ExecutionCountStore.getCount("com.example.Test", 10));
    assertEquals(1, ExecutionCountStore.getCount("com.example.Test", 20));
    assertEquals(0, ExecutionCountStore.getCount("com.example.Test", 30));
    assertEquals(0, ExecutionCountStore.getCount("NonExistent", 10));
  }

  @Test
  void testGetAllCountersSnapshot() {
    ExecutionCountStore.recordExecution("A", 1);
    ExecutionCountStore.recordExecution("B", 2);

    Map<String, Map<Integer, Long>> snapshot = ExecutionCountStore.getAllCountersSnapshot();
    assertEquals(2, snapshot.size());
    assertEquals(1L, snapshot.get("A").get(1));
    assertEquals(1L, snapshot.get("B").get(2));

    // Verify it's a deep copy (modifying snapshot shouldn't affect store)
    snapshot.get("A").put(1, 100L);
    assertEquals(1, ExecutionCountStore.getCount("A", 1));
  }

  @Test
  void testReset() {
    ExecutionCountStore.recordExecution("A", 1);
    ExecutionCountStore.reset();
    assertEquals(0, ExecutionCountStore.getCount("A", 1));
    assertTrue(ExecutionCountStore.getAllCountersSnapshot().isEmpty());
  }

  @Test
  void testThreadSafety() throws InterruptedException {
    int threads = 10;
    int incrementsPerThread = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < incrementsPerThread; j++) {
                ExecutionCountStore.recordExecution("ThreadSafeTest", 1);
              }
            } finally {
              latch.countDown();
            }
          });
    }

    assertTrue(latch.await(5, TimeUnit.SECONDS));
    executor.shutdown();

    assertEquals(threads * incrementsPerThread, ExecutionCountStore.getCount("ThreadSafeTest", 1));
  }
}
