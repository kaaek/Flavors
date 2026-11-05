package com.flavors;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway Postgres container for tests. {@code @ServiceConnection} points the
 * app's datasource at it, so tests need no PG* variables. Import this into any test that
 * needs the database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same major version as local dev and Railway.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
