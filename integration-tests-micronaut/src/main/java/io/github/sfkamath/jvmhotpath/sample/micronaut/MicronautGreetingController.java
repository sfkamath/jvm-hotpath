package io.github.sfkamath.jvmhotpath.sample.micronaut;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/hello")
public class MicronautGreetingController {
    private final MicronautGreetingService greetingService;

    public MicronautGreetingController(MicronautGreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @Get
    public String index() {
        return greetingService.getGreeting();
    }
}
