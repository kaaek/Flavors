package com.flavors.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Turns the free-text search box into the words both searches (creators for brands, brands for
 * creators) match on, so they behave identically.
 */
public final class SearchText {

    /** Longer input is cut, so one request can't build a huge SQL condition. */
    public static final int MAX_LENGTH = 100;

    /** Each word adds a condition to the query; more than this is never useful. */
    public static final int MAX_WORDS = 6;

    private SearchText() {
    }

    /** Trimmed, lowercased words of {@code q}; empty when {@code q} is null or blank. */
    public static List<String> words(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String trimmed = q.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        return Arrays.stream(trimmed.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(word -> !word.isEmpty())
                .limit(MAX_WORDS)
                .toList();
    }

    /**
     * Makes {@code text} safe to put inside a LIKE pattern: {@code %}, {@code _} and the escape
     * character itself would otherwise act as wildcards typed by the user.
     */
    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code %text%}, escaped, for a case-insensitive "contains" match. */
    public static String containsPattern(String text) {
        return "%" + escapeLike(text) + "%";
    }

    /** Trimmed and capped at {@link #MAX_LENGTH}; null when blank. Used for the filter fields. */
    public static String cleanFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }
}
