package io.github.sfkamath.jvmhotpath.gradle;

import org.gradle.api.provider.Property;

public interface JvmHotpathExtension {
  Property<String> getPackages();

  Property<String> getExclude();

  Property<Integer> getFlushInterval();

  Property<String> getOutput();

  Property<String> getSourcepath();

  Property<Boolean> getVerbose();

  Property<Boolean> getKeepAlive();

  Property<Boolean> getAppend();

  Property<Boolean> getInstrumentTests();

  Property<Boolean> getSkip();
}
