package com.example.template;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Backs {@code ./mvnw spring-boot:test-run} with a throwaway database. The Postgres version
 * is pinned identically everywhere — compose.yaml, here, and prod; {@code latest} is banned.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18");
    }
}
