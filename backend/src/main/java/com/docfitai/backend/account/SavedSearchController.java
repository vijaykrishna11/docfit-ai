package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.RenameSavedSearchRequest;
import com.docfitai.backend.account.dto.SaveSearchRequest;
import com.docfitai.backend.account.dto.SavedSearchDto;
import com.docfitai.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Requires authentication. Rows only ever exist because a user explicitly clicked "Save this
 * search" (see SavedSearchService / frontend) -- this endpoint never auto-records searches.
 */
@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    public SavedSearchController(SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    @GetMapping
    public List<SavedSearchDto> list(Authentication authentication) {
        return savedSearchService.list(requireUserId(authentication));
    }

    @PostMapping
    public ResponseEntity<SavedSearchDto> save(Authentication authentication, @Valid @RequestBody SaveSearchRequest request) {
        SavedSearchDto dto = savedSearchService.save(requireUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/{id}")
    public SavedSearchDto rename(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody RenameSavedSearchRequest request) {
        return savedSearchService.rename(requireUserId(authentication), id, request.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable Long id) {
        savedSearchService.remove(requireUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
