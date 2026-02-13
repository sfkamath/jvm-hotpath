package io.github.sfkamath.jvmhotpath.sample;

import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class GreetingService {
  public @NotBlank String getGreeting() {
    return "Hello from Spring Boot!";
  }
}
