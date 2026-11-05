package com.flavors.service.impl;

import com.flavors.dto.BrandSearchCriteria;
import com.flavors.dto.BrandSearchResult;
import com.flavors.dto.SearchPage;
import com.flavors.mapper.BrandSearchMapper;
import com.flavors.repository.BrandSearchRepository;
import com.flavors.repository.BrandSearchRow;
import com.flavors.repository.SearchPageRows;
import com.flavors.service.BrandSearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandSearchServiceImpl implements BrandSearchService {

    private final BrandSearchRepository repository;
    private final BrandSearchMapper mapper;

    public BrandSearchServiceImpl(BrandSearchRepository repository, BrandSearchMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchPage<BrandSearchResult> search(BrandSearchCriteria criteria) {
        SearchPageRows<BrandSearchRow> found = repository.search(criteria);
        return new SearchPage<>(found.rows().stream().map(mapper::toResult).toList(),
                criteria.page(), criteria.size(), found.total());
    }
}
