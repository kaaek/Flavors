package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.SavedCreatorResponse;
import com.flavors.mapper.SavedCreatorMapper;
import com.flavors.service.SavedCreatorService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brand/saved-creators")
public class SavedCreatorController {

    private final SavedCreatorService savedCreatorService;
    private final SavedCreatorMapper savedCreatorMapper;

    public SavedCreatorController(
            SavedCreatorService savedCreatorService,
            SavedCreatorMapper savedCreatorMapper) {
        this.savedCreatorService = savedCreatorService;
        this.savedCreatorMapper = savedCreatorMapper;
    }
    // Use the authenticated brand ID from the session, never an ID supplied in the URL.
    @PostMapping("/{creatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveCreator(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long creatorId) {
        savedCreatorService.saveCreator(user.getId(), creatorId);
    }

    @DeleteMapping("/{creatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCreator(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long creatorId) {
        savedCreatorService.removeCreator(user.getId(), creatorId);
    }

    @GetMapping
    public List<SavedCreatorResponse> getSavedCreators(
            @AuthenticationPrincipal CurrentUser user) {
        return savedCreatorService.getSavedCreators(user.getId()).stream()
                .map(savedCreatorMapper::toResponse)
                .toList();
    }
}
