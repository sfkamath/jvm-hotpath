package io.github.sfkamath.jvmhotpath.sample.micronaut;

/** Fast-exit probe class used by integration tests that exercise exec.mainClass resolution. */
public final class MainClassProbe {
  public static void main(String[] args) {
    // Intentionally empty: the process should start and exit immediately.
  }

  private MainClassProbe() {}
}
