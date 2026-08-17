package com.docfitai.backend.navigator;

import com.docfitai.backend.auth.AuthenticatedUser;
import com.docfitai.backend.navigator.dto.CreateReminderRequest;
import com.docfitai.backend.navigator.dto.ReminderDto;
import com.docfitai.backend.navigator.dto.UpdateReminderRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Requires authentication. Ownership is always resolved from the validated access token (CLAUDE.md "Authorization Integration Tests"). */
@RestController
@RequestMapping("/api/account/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public List<ReminderDto> list(Authentication authentication) {
        return reminderService.list(requireUserId(authentication));
    }

    @PostMapping
    public ResponseEntity<ReminderDto> create(Authentication authentication, @Valid @RequestBody CreateReminderRequest request) {
        ReminderDto dto = reminderService.create(
                requireUserId(authentication), request.title(), request.dueAt(), request.providerId(), request.shortlistId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody UpdateReminderRequest request) {
        reminderService.setCompleted(requireUserId(authentication), id, request.completed());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        reminderService.delete(requireUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
