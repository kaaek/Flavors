package com.flavors.repository;

import java.util.List;

/** The rows of one page plus the total number of matches over all pages. */
public record SearchPageRows<T>(List<T> rows, long total) {
}
