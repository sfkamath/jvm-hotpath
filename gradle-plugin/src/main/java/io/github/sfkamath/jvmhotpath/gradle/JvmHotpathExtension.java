package io.github.sfkamath.jvmhotpath.gradle;

import org.gradle.api.provider.Property;

/** Configuration extension for the JVM Hotpath Gradle plugin. */
public interface JvmHotpathExtension {
  /**
   * Comma-separated package prefixes to instrument (e.g. {@code com.example}).
   *
   * @return the packages property
   */
  Property<String> getPackages();

  /**
   * Comma-separated package prefixes to exclude from instrumentation.
   *
   * @return the exclude property
   */
  Property<String> getExclude();

  /**
   * Interval in seconds at which the agent flushes the report to disk. 0 disables periodic
   * flushing.
   *
   * @return the flushInterval property
   */
  Property<Integer> getFlushInterval();

  /**
   * Path to the generated HTML report file.
   *
   * @return the output property
   */
  Property<String> getOutput();

  /**
   * Colon-separated source root directories or JARs used to resolve source for the report.
   *
   * @return the sourcepath property
   */
  Property<String> getSourcepath();

  /**
   * Enables verbose agent logging when {@code true}.
   *
   * @return the verbose property
   */
  Property<Boolean> getVerbose();

  /**
   * Keeps the JVM alive after the main class exits to allow the report to be written.
   *
   * @return the keepAlive property
   */
  Property<Boolean> getKeepAlive();

  /**
   * Merges execution counts from a previous run's report when {@code true}.
   *
   * @return the append property
   */
  Property<Boolean> getAppend();

  /**
   * Attaches the agent to test tasks when {@code true}. Defaults to {@code false}.
   *
   * @return the instrumentTests property
   */
  Property<Boolean> getInstrumentTests();

  /**
   * Skips all agent configuration when {@code true}.
   *
   * @return the skip property
   */
  Property<Boolean> getSkip();
}
