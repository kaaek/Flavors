package com.flavors.service;

import com.flavors.dto.BrandSearchCriteria;
import com.flavors.dto.BrandSearchResult;
import com.flavors.dto.SearchPage;

public interface BrandSearchService {

    SearchPage<BrandSearchResult> search(BrandSearchCriteria criteria);
}
