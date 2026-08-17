package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.CreateShortlistRequest;
import com.docfitai.backend.account.dto.RenameShortlistRequest;
import com.docfitai.backend.account.dto.ShortlistDetailDto;
import com.docfitai.backend.account.dto.ShortlistDto;
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

/** Requires authentication. Ownership is always resolved from the validated access token, never the client (CLAUDE.md "Shortlist Authorization"). */
@RestController
@RequestMapping("/api/account/shortlists")
public class ShortlistController {

    private final ShortlistService shortlistService;

    public ShortlistController(ShortlistService shortlistService) {
        this.shortlistService = shortlistService;
    }

    @GetMapping
    public List<ShortlistDto> list(Authentication authentication) {
        return shortlistService.list(requireUserId(authentication));
    }

    @PostMapping
    public ResponseEntity<ShortlistDto> create(Authentication authentication, @Valid @RequestBody CreateShortlistRequest request) {
        ShortlistDto dto = shortlistService.create(requireUserId(authentication), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ShortlistDetailDto getDetail(Authentication authentication, @PathVariable Long id) {
        return shortlistService.getDetail(requireUserId(authentication), id);
    }

    @PatchMapping("/{id}")
    public ShortlistDto rename(Authentication authentication, @PathVariable Long id, @Valid @RequestBody RenameShortlistRequest request) {
        return shortlistService.rename(requireUserId(authentication), id, request.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        shortlistService.delete(requireUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/providers/{providerId}")
    public ResponseEntity<Void> addProvider(Authentication authentication, @PathVariable Long id, @PathVariable Long providerId) {
        shortlistService.addProvider(requireUserId(authentication), id, providerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/providers/{providerId}")
    public ResponseEntity<Void> removeProvider(Authentication authentication, @PathVariable Long id, @PathVariable Long providerId) {
        shortlistService.removeProvider(requireUserId(authentication), id, providerId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
