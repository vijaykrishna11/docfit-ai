package com.docfitai.backend.report;

import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.report.dto.ReportSubmittedDto;
import com.docfitai.backend.report.dto.SubmitReportRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates and stores directory-data correction reports as review signals only (CLAUDE.md
 * "Report Security"): provider must exist, a supplied location must actually belong to that
 * provider, report type is constrained to the {@link ReportType} enum (an unknown value is
 * rejected by JSON deserialization itself, before this class ever runs), and comment length is
 * bounded. Nothing here writes to provider/provider_location -- this table is read-only from the
 * rest of the application's perspective.
 */
@Service
public class ProviderDataReportService {

    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ProviderDataReportRepository reportRepository;
    private final ReportRateLimiter rateLimiter;

    public ProviderDataReportService(
            ProviderRepository providerRepository,
            ProviderLocationRepository providerLocationRepository,
            ProviderDataReportRepository reportRepository,
            ReportRateLimiter rateLimiter) {
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
        this.reportRepository = reportRepository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public ReportSubmittedDto submit(Long providerId, SubmitReportRequest request, Long userId, String clientIp) {
        rateLimiter.checkAllowed(clientIp);

        if (!providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        if (request.providerLocationId() != null
                && !providerLocationRepository.existsByIdAndProviderId(request.providerLocationId(), providerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That location does not belong to this provider.");
        }

        ProviderDataReport saved = reportRepository.save(new ProviderDataReport(
                providerId, request.providerLocationId(), userId, request.reportType(), blankToNull(request.comment()), Instant.now()));
        return new ReportSubmittedDto(saved.getId());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
