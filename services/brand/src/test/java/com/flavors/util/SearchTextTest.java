package com.flavors.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void splitsAndLowercasesWords() {
        assertThat(SearchText.words("  Burgers   BEIRUT ")).containsExactly("burgers", "beirut");
    }

    @Test
    void blankOrNullGivesNoWords() {
        assertThat(SearchText.words(null)).isEmpty();
        assertThat(SearchText.words("   ")).isEmpty();
    }

    @Test
    void capsTheNumberOfWords() {
        assertThat(SearchText.words("a b c d e f g h")).hasSize(SearchText.MAX_WORDS);
    }

    @Test
    void capsTheLength() {
        String longText = "x".repeat(500);
        assertThat(SearchText.words(longText)).singleElement()
                .satisfies(word -> assertThat(word).hasSize(SearchText.MAX_LENGTH));
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(SearchText.escapeLike("100%_off\\")).isEqualTo("100\\%\\_off\\\\");
        assertThat(SearchText.containsPattern("a%b")).isEqualTo("%a\\%b%");
    }

    @Test
    void cleanFilterTrimsAndTurnsBlankIntoNull() {
        assertThat(SearchText.cleanFilter("  Beirut ")).isEqualTo("Beirut");
        assertThat(SearchText.cleanFilter("  ")).isNull();
        assertThat(SearchText.cleanFilter(null)).isNull();
    }
}
