package io.github.jayessdeesea.minecraft.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Component scan is widened to the shared root package
 * because command-ai/bot/protocol beans live outside this module's own
 * package.
 */
@SpringBootApplication(scanBasePackages = "io.github.jayessdeesea.minecraft")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
