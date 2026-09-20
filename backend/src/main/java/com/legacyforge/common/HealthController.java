package com.legacyforge.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public endpoints for basic health and greeting.
 * Real health goes through Spring Actuator at /actuator/health.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello from LegacyForge backend",
                "service", "legacyforge-backend",
                "version", "0.1.0",
                "timestamp", Instant.now().toString()
        );
    }
}
