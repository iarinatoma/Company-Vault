package com.example.template.integration;

import com.github.database.rider.core.api.configuration.DBUnit;
import com.github.database.rider.spring.api.DBRider;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * One Spring context and one Testcontainers Postgres for the whole run. The database is not
 * reset between test classes: every test must survive data left behind by others — mint
 * unique values for anything uniquely constrained, never assume an empty table, and never
 * touch another class's fixtures. Fixture rows must state id/created_at/updated_at/version
 * explicitly ({@code version: 0} is the convention) because BaseEntity sets those on the
 * Java side only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@DBRider
// caseSensitiveTableNames=true: DBUnit would otherwise upper-case names and miss the
// lowercase Postgres tables during dependency resolution.
@DBUnit(caseSensitiveTableNames = true, schema = "public", alwaysCleanBefore = false, alwaysCleanAfter = false)
public abstract class AbstractIntegrationTest {

    // The Postgres version is pinned identically everywhere; latest is banned.
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /** The response body, asserted present — {@code ResponseEntity#getBody} is nullable by contract. */
    protected static JsonNode bodyOf(org.springframework.http.ResponseEntity<JsonNode> response) {
        return Objects.requireNonNull(response.getBody(), "expected a response body");
    }
}
