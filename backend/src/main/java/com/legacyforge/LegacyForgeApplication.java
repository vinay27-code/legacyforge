package com.legacyforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LegacyForge backend entrypoint.
 *
 * Agentic AI platform that migrates legacy Java monoliths to Spring Boot 3
 * + Angular, with human in the loop review.
 */
@SpringBootApplication
public class LegacyForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyForgeApplication.class, args);
    }
}
