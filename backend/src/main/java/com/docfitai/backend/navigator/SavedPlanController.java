package com.docfitai.backend.navigator;

import com.docfitai.backend.auth.AuthenticatedUser;
import com.docfitai.backend.navigator.dto.SavedPlanDto;
import com.docfitai.backend.navigator.dto.SaveSavedPlanRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Requires authentication. Identity is always resolved from the validated access token, never a client-supplied userId (CLAUDE.md "Saved Plan API"). */
@RestController
@RequestMapping("/api/account/saved-plan")
public class SavedPlanController {

    private final SavedPlanService savedPlanService;

    public SavedPlanController(SavedPlanService savedPlanService) {
        this.savedPlanService = savedPlanService;
    }

    @GetMapping
    public ResponseEntity<SavedPlanDto> get(Authentication authentication) {
        SavedPlanDto dto = savedPlanService.get(requireUserId(authentication));
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }

    @PutMapping
    public SavedPlanDto save(Authentication authentication, @Valid @RequestBody SaveSavedPlanRequest request) {
        return savedPlanService.save(requireUserId(authentication), request.insurancePlanId());
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(Authentication authentication) {
        savedPlanService.remove(requireUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
