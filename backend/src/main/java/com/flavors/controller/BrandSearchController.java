package com.flavors.controller;

import com.flavors.dto.BrandSearchCriteria;
import com.flavors.dto.BrandSearchResult;
import com.flavors.dto.SearchPage;
import com.flavors.service.BrandSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creators search brands. CREATOR-only through the /api/creator/** rule. */
@RestController
@RequestMapping("/api/creator/brands")
public class BrandSearchController {

    private final BrandSearchService search;

    public BrandSearchController(BrandSearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    SearchPage<BrandSearchResult> search(@Valid BrandSearchCriteria criteria) {
        return search.search(criteria);
    }
}
