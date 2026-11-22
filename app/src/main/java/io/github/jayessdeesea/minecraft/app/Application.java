package io.github.jayessdeesea.minecraft.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;

/**
 * Composition root. Component scan is widened to the shared root package
 * because command-ai/bot/protocol beans live outside this module's own
 * package.
 */
@SpringBootApplication(scanBasePackages = "io.github.jayessdeesea.minecraft")
public class Application {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Application.class);
        // Its process id to spring.pid.file, for a deploy to stop this run by - not read back out of a log.
        application.addListeners(new ApplicationPidFileWriter());
        application.run(args);
    }
}
