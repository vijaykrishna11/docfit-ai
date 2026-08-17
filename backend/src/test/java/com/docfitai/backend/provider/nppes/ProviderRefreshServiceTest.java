package com.docfitai.backend.provider.nppes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.docfitai.backend.provider.ingestion.DataImport;
import com.docfitai.backend.provider.ingestion.DataImportRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CLAUDE.md "Operator-Triggerable Provider Refresh": exercises the refresh logic against a
 * mocked {@link NppesClient} -- CI never calls the live NPPES API for this. Everything else
 * (upsert, taxonomy lookup, data quality checks, DataImport bookkeeping) runs for real against the
 * test database, so this proves the whole path except the actual HTTP call.
 */
@ExtendWith(MockitoExtension.class)
class ProviderRefreshServiceTest extends PostgresIntegrationSupport {

    @Mock
    private NppesClient nppesClient;

    @Autowired
    private NppesRecordFactory recordFactory;

    @Autowired
    private com.docfitai.backend.provider.ingestion.ProviderUpsertService providerUpsertService;

    @Autowired
    private DataImportRepository dataImportRepository;

    @Autowired
    private com.docfitai.backend.provider.ingestion.ProviderDataQualityService dataQualityService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private ProviderRefreshService newService() {
        return new ProviderRefreshService(
                nppesClient, recordFactory, providerUpsertService, dataImportRepository, dataQualityService, jdbcTemplate);
    }

    @Test
    void emptyNpiListDoesNothingAndReturnsAZeroSummary() {
        ProviderRefreshService.RefreshSummary summary = newService().refreshByNpis(List.of());

        assertThat(summary.npisRequested()).isZero();
        assertThat(summary.providersCreated()).isZero();
        assertThat(summary.providersUpdated()).isZero();
    }

    @Test
    void npiNotFoundInNppesIsCountedAsNotFoundNotFailed() {
        when(nppesClient.lookupByNpi(anyString())).thenReturn(new NppesResponse(0, List.of()));

        ProviderRefreshService.RefreshSummary summary = newService().refreshByNpis(List.of("1000000001"));

        assertThat(summary.npisRequested()).isEqualTo(1);
        assertThat(summary.notFoundOrUnmapped()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
    }

    @Test
    void aClientExceptionForOneNpiIsCaughtAndCountedAsFailedWithoutAbortingTheRun() {
        when(nppesClient.lookupByNpi("1000000002")).thenThrow(new IllegalStateException("simulated NPPES failure"));
        when(nppesClient.lookupByNpi("1000000003")).thenReturn(new NppesResponse(0, List.of()));

        ProviderRefreshService.RefreshSummary summary =
                newService().refreshByNpis(List.of("1000000002", "1000000003"));

        assertThat(summary.npisRequested()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.notFoundOrUnmapped()).isEqualTo(1);
    }

    @Test
    void recordsADataImportRowScopedPartial() {
        when(nppesClient.lookupByNpi(anyString())).thenReturn(new NppesResponse(0, List.of()));
        long before = dataImportRepository.count();

        newService().refreshByNpis(List.of("1000000004"));

        assertThat(dataImportRepository.count()).isEqualTo(before + 1);
        DataImport latest = dataImportRepository.findAll().stream()
                .max(java.util.Comparator.comparing(DataImport::getId))
                .orElseThrow();
        assertThat(latest.getSource()).isEqualTo("NPPES_REFRESH");
        assertThat(latest.getScopeType()).isEqualTo("PARTIAL");
    }
}
