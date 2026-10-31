package com.flavors.controller;
import com.flavors.dto.CurrentUser;
import com.flavors.entity.SavedCreator;
import com.flavors.service.SavedCreatorService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.flavors.dto.SavedCreatorResponse;
@RestController
@RequestMapping("/api/brand/saved-creators")
public class SavedCreatorController {
    private final SavedCreatorService savedCreatorService;
    public SavedCreatorController(SavedCreatorService savedCreatorService) {
        this.savedCreatorService = savedCreatorService;
    }
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
    public List<SavedCreatorResponse> getSavedCreators(@AuthenticationPrincipal CurrentUser user) {
          return savedCreatorService.getSavedCreators(user.getId()).stream()
            .map(saved -> new SavedCreatorResponse(saved.getCreatorId(),saved.getCreatedAt()))
            .toList();
    }
}
