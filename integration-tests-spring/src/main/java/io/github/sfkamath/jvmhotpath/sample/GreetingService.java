package io.github.sfkamath.jvmhotpath.sample;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
  public String getGreeting() {
    return "Hello from Spring Boot!";
  }
}
