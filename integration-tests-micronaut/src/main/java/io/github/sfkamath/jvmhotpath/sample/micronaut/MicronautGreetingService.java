package io.github.sfkamath.jvmhotpath.sample.micronaut;

import jakarta.inject.Singleton;

@Singleton
public class MicronautGreetingService {
  public String getGreeting() {
    return "Hello from Micronaut!";
  }
}
