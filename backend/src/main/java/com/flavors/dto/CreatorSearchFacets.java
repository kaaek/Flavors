package com.flavors.dto;

import java.util.List;

/** Values that exist among approved creators, to suggest in the filter boxes. */
public record CreatorSearchFacets(List<String> locations, List<String> categories) {
}
