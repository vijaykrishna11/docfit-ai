package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderNameSearchResultDto;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Secondary "already know who you're looking for" lookup by provider/organization name --
 * a small, safely-parameterized ILIKE query. Postgres is sufficient at this data scale; no
 * search engine needed.
 */
@Service
public class ProviderNameSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_RESULTS = 10;

    private static final String QUERY =
            """
            SELECT DISTINCT ON (p.id) p.id, p.npi_number, p.first_name, p.last_name, p.organization_name,
                   p.city, p.state_code, nt.display_name
            FROM provider p
            JOIN provider_taxonomy pt ON pt.provider_id = p.id
            JOIN npi_taxonomy nt ON nt.taxonomy_code = pt.taxonomy_code
            WHERE p.first_name ILIKE ? OR p.last_name ILIKE ? OR p.organization_name ILIKE ?
            ORDER BY p.id, pt.primary_taxonomy DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProviderNameSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProviderNameSearchResultDto> search(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String pattern = "%" + query.trim() + "%";
        return jdbcTemplate.query(
                QUERY,
                (rs, rowNum) -> new ProviderNameSearchResultDto(
                        rs.getLong("id"),
                        rs.getString("npi_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("organization_name"),
                        rs.getString("city"),
                        rs.getString("state_code"),
                        rs.getString("display_name")),
                pattern,
                pattern,
                pattern,
                MAX_RESULTS);
    }
}
