package com.legacyforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the Spring context loads.
 * Uses the "test" profile which points at Testcontainers, wired in a later week.
 */
@SpringBootTest
@ActiveProfiles("test")
class LegacyForgeApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the Spring context boots without errors.
    }
}
