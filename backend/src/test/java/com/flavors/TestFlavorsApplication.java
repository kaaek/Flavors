package com.flavors;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally against a Testcontainers Postgres, with no docker-compose and no PG*
 * variables. Start it from VS Code ("Run" above main) or with {@code ./mvnw spring-boot:test-run}.
 */
public class TestFlavorsApplication {

    public static void main(String[] args) {
        SpringApplication.from(FlavorsApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
