package com.flavors.service;

import com.flavors.dto.CreatorSearchCriteria;
import com.flavors.dto.CreatorSearchFacets;
import com.flavors.dto.CreatorSearchResult;
import com.flavors.dto.SearchPage;

public interface CreatorSearchService {

    /** Approved creators matching {@code criteria}; {@code saved} is relative to {@code brandId}. */
    SearchPage<CreatorSearchResult> search(long brandId, CreatorSearchCriteria criteria);

    CreatorSearchFacets facets();
}
