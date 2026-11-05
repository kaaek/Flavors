package com.flavors.service.impl;

import com.flavors.dto.CreatorSearchCriteria;
import com.flavors.dto.CreatorSearchFacets;
import com.flavors.dto.CreatorSearchResult;
import com.flavors.dto.SearchPage;
import com.flavors.mapper.CreatorSearchMapper;
import com.flavors.repository.CreatorSearchRepository;
import com.flavors.repository.CreatorSearchRow;
import com.flavors.repository.SearchPageRows;
import com.flavors.service.CreatorSearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorSearchServiceImpl implements CreatorSearchService {

    private final CreatorSearchRepository repository;
    private final CreatorSearchMapper mapper;

    public CreatorSearchServiceImpl(CreatorSearchRepository repository, CreatorSearchMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchPage<CreatorSearchResult> search(long brandId, CreatorSearchCriteria criteria) {
        SearchPageRows<CreatorSearchRow> found = repository.search(brandId, criteria);
        return new SearchPage<>(found.rows().stream().map(mapper::toResult).toList(),
                criteria.page(), criteria.size(), found.total());
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorSearchFacets facets() {
        return repository.facets();
    }
}
