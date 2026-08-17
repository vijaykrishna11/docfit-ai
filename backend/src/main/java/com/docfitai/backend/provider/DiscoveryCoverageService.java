package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.CoverageDto;
import com.docfitai.backend.reference.SpecialtyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Real, runtime-queried counts only (CLAUDE.md "Coverage API V2" / "Coverage Transparency UI") --
 * never a hardcoded or marketing number. Cheap enough (a handful of COUNT/MAX queries against
 * small tables) that no caching was added (CLAUDE.md "Cache": "Cache lightly if expensive" -- this
 * isn't).
 *
 * <p>Deliberately reports reference geography (what DocFit knows about) and actual provider
 * coverage (where provider data was really imported) as two separate figures -- see
 * {@link CoverageDto}'s doc comment for why.
 */
@Service
public class DiscoveryCoverageService {

    private static final int MAX_SAMPLE_AREAS = 12;

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

        Long geographyZipCount = jdbcTemplate.queryForObject("SELECT count(*) FROM zip_geography", Long.class);
        Long geographyCityCount = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT city) FROM zip_geography WHERE city IS NOT NULL", Long.class);
        Long geographyCountyCount = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT county) FROM zip_geography WHERE county IS NOT NULL", Long.class);
        String geographySource = resolveGeographySource();

        Long providerZipCount = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT postal_code) FROM provider_location", Long.class);
        Long providerCityCount = jdbcTemplate.queryForObject(
                """
                SELECT count(DISTINCT zg.city)
                FROM provider_location pl
                JOIN zip_geography zg ON zg.zip_code = pl.postal_code
                WHERE zg.city IS NOT NULL
                """,
                Long.class);
        List<String> providerAreas = jdbcTemplate.query(
                """
                SELECT DISTINCT zg.city, zg.state_code
                FROM provider_location pl
                JOIN zip_geography zg ON zg.zip_code = pl.postal_code
                WHERE zg.city IS NOT NULL
                ORDER BY zg.city
                """,
                (rs, rowNum) -> rs.getString("city") + ", " + rs.getString("state_code"));

        Timestamp lastImport = jdbcTemplate.queryForObject(
                "SELECT MAX(completed_at) FROM data_import WHERE status IN ('COMPLETED', 'PARTIAL')", Timestamp.class);
        Instant lastImportCompletedAt = lastImport == null ? null : lastImport.toInstant();

        return new CoverageDto(
                providerCount,
                locationCount,
                specialtyCount,
                geographyZipCount == null ? 0 : geographyZipCount,
                geographyCityCount == null ? 0 : geographyCityCount,
                geographyCountyCount == null ? 0 : geographyCountyCount,
                geographySource,
                providerZipCount == null ? 0 : providerZipCount,
                providerCityCount == null ? 0 : providerCityCount,
                providerAreas.stream().limit(MAX_SAMPLE_AREAS).toList(),
                providerAreas.size() > MAX_SAMPLE_AREAS,
                lastImportCompletedAt);
    }

    /** A short, honest description of where the loaded reference geography came from -- never fabricated if sources are mixed. */
    private String resolveGeographySource() {
        List<String> sources = jdbcTemplate.query(
                """
                SELECT DISTINCT source_name, source_version
                FROM zip_geography
                WHERE source_name IS NOT NULL
                ORDER BY source_name
                """,
                (rs, rowNum) -> {
                    String version = rs.getString("source_version");
                    return rs.getString("source_name") + (version != null ? " (" + version + ")" : "");
                });
        if (sources.isEmpty()) {
            return null;
        }
        if (sources.size() == 1) {
            return sources.get(0);
        }
        return sources.size() + " distinct sources";
    }
}
