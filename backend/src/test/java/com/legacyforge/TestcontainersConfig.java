package com.legacyforge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a pgvector-enabled Postgres container for every @SpringBootTest that
 * imports this config. Spring Boot's @ServiceConnection wires the JDBC URL
 * automatically, so application-test.yml doesn't have to know about it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> pgvectorContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres")
        )
        .withDatabaseName("legacyforge_test")
        .withUsername("test")
        .withPassword("test");
    }
}
