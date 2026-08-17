package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.CoverageDto;
import com.docfitai.backend.reference.SpecialtyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Real, runtime-queried counts only (CLAUDE.md "Coverage API" / "Coverage Transparency UI") --
 * never a hardcoded or marketing number. Cheap enough (a handful of COUNT/MAX queries against
 * small tables) that no caching was added (CLAUDE.md "Cache": "Cache lightly if expensive" -- this
 * isn't).
 */
@Service
public class DiscoveryCoverageService {

    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final JdbcTemplate jdbcTemplate;

    public DiscoveryCoverageService(
            ProviderRepository providerRepository,
            ProviderLocationRepository providerLocationRepository,
            SpecialtyRepository specialtyRepository,
            JdbcTemplate jdbcTemplate) {
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
        this.specialtyRepository = specialtyRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CoverageDto getCoverage() {
        long providerCount = providerRepository.count();
        long locationCount = providerLocationRepository.count();
        int specialtyCount = (int) specialtyRepository.count();
        List<String> areas = jdbcTemplate.query(
                "SELECT DISTINCT city, state_code FROM zip_geography ORDER BY city",
                (rs, rowNum) -> rs.getString("city") + ", " + rs.getString("state_code"));
        Timestamp lastImport = jdbcTemplate.queryForObject(
                "SELECT MAX(completed_at) FROM data_import WHERE status IN ('COMPLETED', 'PARTIAL')", Timestamp.class);
        Instant lastImportCompletedAt = lastImport == null ? null : lastImport.toInstant();
        return new CoverageDto(providerCount, locationCount, specialtyCount, areas, lastImportCompletedAt);
    }
}
