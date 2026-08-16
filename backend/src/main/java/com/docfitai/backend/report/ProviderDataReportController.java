package com.docfitai.backend.report;

import com.docfitai.backend.auth.AuthenticatedUser;
import com.docfitai.backend.report.dto.ReportSubmittedDto;
import com.docfitai.backend.report.dto.SubmitReportRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous submission is allowed (CLAUDE.md "Report Privacy": chosen deliberately -- see
 * docs/directory-corrections.md for the rationale), rate-limited by IP either way. If the caller
 * happens to be signed in, the report is tagged with their userId for context; that is never
 * required. No endpoint here ever reads reports back -- see ProviderDataReportRepository.
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderDataReportController {

    private final ProviderDataReportService reportService;

    public ProviderDataReportController(ProviderDataReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/{providerId}/reports")
    public ResponseEntity<ReportSubmittedDto> submit(
            @PathVariable Long providerId,
            @Valid @RequestBody SubmitReportRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long userId = (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal)
                ? principal.userId()
                : null;
        ReportSubmittedDto result = reportService.submit(providerId, request, userId, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
