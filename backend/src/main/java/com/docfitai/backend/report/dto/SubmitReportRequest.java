package com.docfitai.backend.report.dto;

import com.docfitai.backend.report.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitReportRequest(
        @NotNull(message = "reportType is required") ReportType reportType,
        Long providerLocationId,
        @Size(max = 1000, message = "Comment is too long") String comment) {
}
