package com.legacyforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

/**
 * LegacyForge backend entrypoint.
 *
 * NOTE: Redis autoconfiguration is disabled until Week 5, when we wire it in
 * as a job queue.
 */
@SpringBootApplication(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class
})
public class LegacyForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyForgeApplication.class, args);
    }
}
