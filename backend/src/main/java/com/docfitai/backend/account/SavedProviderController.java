package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.SavedProviderDto;
import com.docfitai.backend.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Requires authentication (see SecurityConfig); the user id always comes from the validated access token, never the client. */
@RestController
@RequestMapping("/api/saved-providers")
public class SavedProviderController {

    private final SavedProviderService savedProviderService;

    public SavedProviderController(SavedProviderService savedProviderService) {
        this.savedProviderService = savedProviderService;
    }

    @GetMapping
    public List<SavedProviderDto> list(Authentication authentication) {
        return savedProviderService.list(requireUserId(authentication));
    }

    @PostMapping("/{providerId}")
    public ResponseEntity<Void> save(Authentication authentication, @PathVariable Long providerId) {
        savedProviderService.save(requireUserId(authentication), providerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{providerId}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable Long providerId) {
        savedProviderService.remove(requireUserId(authentication), providerId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
