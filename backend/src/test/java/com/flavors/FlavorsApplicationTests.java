package com.flavors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Smoke test: the whole application starts against a real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlavorsApplicationTests {

    @Test
    void contextLoads() {
    }
}
