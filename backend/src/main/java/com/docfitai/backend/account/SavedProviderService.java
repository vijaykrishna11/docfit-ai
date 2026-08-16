package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.SavedProviderDto;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.dto.ProviderLocationDto;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedProviderService {

    // Saved providers show the provider's primary practice location -- there is no search
    // origin/distance context here (CLAUDE.md 17), unlike a search result's nearest-location pick.
    private static final String LIST_QUERY =
            """
            SELECT sp.id, sp.created_at, p.id AS provider_id, p.npi_number, p.entity_type, p.first_name, p.last_name,
                   p.organization_name, pl.id AS location_id, pl.address_line_1, pl.address_line_2, pl.city,
                   pl.state_code, pl.postal_code, pl.phone, pl.latitude, pl.longitude, pl.coordinate_precision
            FROM saved_provider sp
            JOIN provider p ON p.id = sp.provider_id
            LEFT JOIN provider_location pl ON pl.provider_id = p.id AND pl.is_primary = TRUE
            WHERE sp.user_id = ?
            ORDER BY sp.created_at DESC
            """;

    private final SavedProviderRepository savedProviderRepository;
    private final ProviderRepository providerRepository;
    private final JdbcTemplate jdbcTemplate;

    public SavedProviderService(
            SavedProviderRepository savedProviderRepository, ProviderRepository providerRepository, JdbcTemplate jdbcTemplate) {
        this.savedProviderRepository = savedProviderRepository;
        this.providerRepository = providerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SavedProviderDto> list(Long userId) {
        return jdbcTemplate.query(
                LIST_QUERY,
                (rs, rowNum) -> {
                    Long locationId = rs.getLong("location_id");
                    ProviderLocationDto location = rs.wasNull()
                            ? null
                            : new ProviderLocationDto(
                                    locationId,
                                    rs.getString("address_line_1"),
                                    rs.getString("address_line_2"),
                                    rs.getString("city"),
                                    rs.getString("state_code"),
                                    rs.getString("postal_code"),
                                    rs.getString("phone"),
                                    rs.getBigDecimal("latitude"),
                                    rs.getBigDecimal("longitude"),
                                    rs.getString("coordinate_precision"));
                    return new SavedProviderDto(
                            rs.getLong("id"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getLong("provider_id"),
                            rs.getString("npi_number"),
                            rs.getString("entity_type"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("organization_name"),
                            location);
                },
                userId);
    }

    @Transactional
    public void save(Long userId, Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        if (!savedProviderRepository.existsByUserIdAndProviderId(userId, providerId)) {
            savedProviderRepository.save(new SavedProvider(userId, providerId, Instant.now()));
        }
    }

    @Transactional
    public void remove(Long userId, Long providerId) {
        savedProviderRepository.deleteByUserIdAndProviderId(userId, providerId);
    }
}
