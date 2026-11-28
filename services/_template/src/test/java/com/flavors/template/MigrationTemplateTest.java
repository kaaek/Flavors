package com.flavors.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** V1 is a copy of the platform's SQL templates; this fails when one drifts from the other. */
class MigrationTemplateTest {

    private static String statements(String path) throws IOException {
        String sql = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        return sql.lines().filter(line -> !line.strip().startsWith("--")).reduce("", (a, b) -> a + " " + b)
                .replaceAll("\\s+", " ").strip();
    }

    @Test
    void v1ContainsEveryPlatformTemplateVerbatim() throws IOException {
        String v1 = statements("db/migration/V1__platform_foundation.sql");

        for (String template : new String[] {"audit_log", "idempotency_key", "outbox_inbox"}) {
            assertThat(v1).as(template).contains(statements("db/templates/" + template + ".sql"));
        }
    }
}
