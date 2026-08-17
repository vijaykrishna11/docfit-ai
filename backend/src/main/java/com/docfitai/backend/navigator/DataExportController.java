package com.docfitai.backend.navigator;

import com.docfitai.backend.auth.AuthenticatedUser;
import com.docfitai.backend.navigator.dto.UserDataExportDto;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Requires authentication; the export always covers only the authenticated caller -- there is no user-id parameter to accept or validate (CLAUDE.md "Data Export API"). */
@RestController
@RequestMapping("/api/account/export")
public class DataExportController {

    private final DataExportService exportService;

    public DataExportController(DataExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<UserDataExportDto> export(Authentication authentication) {
        UserDataExportDto data = exportService.export(requireUserId(authentication));
        // Filename is a fixed literal, not built from user-supplied input -- no path/header injection surface.
        ContentDisposition disposition =
                ContentDisposition.attachment().filename("docfit-ai-data-export.json").build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(data);
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
