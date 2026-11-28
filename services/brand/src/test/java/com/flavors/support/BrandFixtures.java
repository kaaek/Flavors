package com.flavors.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Rows the monolith's registration used to create for these tests. Registration stays in the monolith until
 * identity merges (docs/Microservices-Brand.md), so the brand service's tests insert the rows directly.
 */
public final class BrandFixtures {

    private BrandFixtures() {
    }

    /** An ACTIVE BRAND user with its brand row and one branch; returns the user id. */
    public static long brand(JdbcTemplate jdbc, String businessName, String cuisine, String description) {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'BRAND', 'ACTIVE')
                RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("""
                INSERT INTO brand (user_id, business_name, contact_person, phone, cuisine, description)
                VALUES (?, ?, 'Karim', '+961 1 234567', ?, ?)
                """, id, businessName, cuisine, description);
        jdbc.update("INSERT INTO branch (brand_id, name, location) VALUES (?, 'Hamra', 'Beirut')", id);
        return id;
    }
}
