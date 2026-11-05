package com.flavors.dto;

import java.util.List;

/** One page of search results. {@code page} starts at 0. */
public record SearchPage<T>(List<T> items, int page, int size, long totalItems) {
}
