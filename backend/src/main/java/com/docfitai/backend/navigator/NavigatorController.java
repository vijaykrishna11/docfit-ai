package com.docfitai.backend.navigator;

import com.docfitai.backend.auth.AuthenticatedUser;
import com.docfitai.backend.navigator.dto.NavigationStatusDto;
import com.docfitai.backend.navigator.dto.NavigatorDashboardDto;
import com.docfitai.backend.navigator.dto.UpdateNavigationStatusRequest;
import com.docfitai.backend.navigator.dto.UpdateVerificationItemRequest;
import com.docfitai.backend.navigator.dto.VerificationItemDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Requires authentication. Every method resolves the acting user from the validated access token, never a client-supplied id. */
@RestController
@RequestMapping("/api/account")
public class NavigatorController {

    private final NavigatorService navigatorService;

    public NavigatorController(NavigatorService navigatorService) {
        this.navigatorService = navigatorService;
    }

    @GetMapping("/navigator")
    public NavigatorDashboardDto getDashboard(Authentication authentication) {
        return navigatorService.getDashboard(requireUserId(authentication));
    }

    @PutMapping("/providers/{providerId}/navigation-status")
    public NavigationStatusDto updateStatus(
            Authentication authentication, @PathVariable Long providerId, @Valid @RequestBody UpdateNavigationStatusRequest request) {
        return navigatorService.updateStatus(requireUserId(authentication), providerId, request.status());
    }

    @GetMapping("/providers/{providerId}/verification-items")
    public List<VerificationItemDto> getVerificationItems(Authentication authentication, @PathVariable Long providerId) {
        return navigatorService.getVerificationItems(requireUserId(authentication), providerId);
    }

    @PutMapping("/providers/{providerId}/verification-items/{type}")
    public VerificationItemDto updateVerificationItem(
            Authentication authentication,
            @PathVariable Long providerId,
            @PathVariable VerificationType type,
            @Valid @RequestBody UpdateVerificationItemRequest request) {
        return navigatorService.updateVerificationItem(
                requireUserId(authentication), providerId, type, request.status(), request.providerLocationId());
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
