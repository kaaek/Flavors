package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreatorReviewServiceImplTest {

    @Test
    void slugIsLowercaseWithHyphensAndEndsWithTheId() {
        assertThat(CreatorReviewServiceImpl.slugFor("Lara Khoury", 7)).isEqualTo("lara-khoury-7");
    }

    @Test
    void accentsAndSymbolsAreRemoved() {
        assertThat(CreatorReviewServiceImpl.slugFor("  Zoé & Co.! ", 12)).isEqualTo("zoe-co-12");
    }

    @Test
    void aNameWithNoLatinLettersFallsBackToCreatorAndTheId() {
        assertThat(CreatorReviewServiceImpl.slugFor("لارا", 9)).isEqualTo("creator-9");
    }

    @Test
    void aVeryLongNameStaysWithinTheColumn() {
        String slug = CreatorReviewServiceImpl.slugFor("a".repeat(200), 123);
        assertThat(slug.length()).isLessThanOrEqualTo(80);
        assertThat(slug).endsWith("-123");
    }
}
