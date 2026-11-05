package com.flavors.controller;

import com.flavors.dto.CreatorSearchCriteria;
import com.flavors.dto.CreatorSearchFacets;
import com.flavors.dto.CreatorSearchResult;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.SearchPage;
import com.flavors.service.CreatorSearchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Brands search approved creators (UC-F2.1). BRAND-only through the /api/brand/** rule. */
@RestController
@RequestMapping("/api/brand/creators")
public class CreatorSearchController {

    private final CreatorSearchService search;

    public CreatorSearchController(CreatorSearchService search) {
        this.search = search;
    }

    // The brand id is only used to mark which results this brand has saved.
    @GetMapping("/search")
    SearchPage<CreatorSearchResult> search(@AuthenticationPrincipal CurrentUser user,
                                           @Valid CreatorSearchCriteria criteria) {
        return search.search(user.getId(), criteria);
    }

    @GetMapping("/facets")
    CreatorSearchFacets facets() {
        return search.facets();
    }
}
