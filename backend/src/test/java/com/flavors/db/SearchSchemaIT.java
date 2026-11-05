package com.flavors.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks what V8__search.sql adds: the pg_trgm extension, the brand columns and the trigram indexes. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SearchSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void trigramExtensionIsInstalled() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class))
                .isEqualTo(1);
        // word_similarity is what the typo-tolerant search relies on.
        assertThat(jdbc.queryForObject("SELECT word_similarity('burgr', 'burgers') > 0.3", Boolean.class)).isTrue();
    }

    @Test
    void brandCuisineAndDescriptionAreOptional() {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'brand' AND column_name IN ('cuisine', 'description') AND is_nullable = 'YES'
                """, String.class)).containsExactlyInAnyOrder("cuisine", "description");
    }

    @Test
    void searchedColumnsHaveTrigramIndexes() {
        assertThat(jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE indexdef LIKE '%gin_trgm_ops%'
                """, String.class)).contains(
                "creator_profile_display_name_trgm_idx", "creator_profile_food_category_trgm_idx",
                "creator_profile_location_trgm_idx", "creator_profile_bio_trgm_idx",
                "brand_business_name_trgm_idx", "brand_cuisine_trgm_idx",
                "branch_name_trgm_idx", "branch_location_trgm_idx");
    }
}
