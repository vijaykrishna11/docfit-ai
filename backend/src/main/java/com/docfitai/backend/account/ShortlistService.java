package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.ShortlistDetailDto;
import com.docfitai.backend.account.dto.ShortlistDto;
import com.docfitai.backend.account.dto.ShortlistProviderDto;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.dto.ProviderLocationDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * All ownership resolved from the authenticated caller's userId, never a client-supplied one
 * (CLAUDE.md "Shortlist Authorization"). A shortlist owned by another user is always a 404, never
 * a 403 -- so a request for someone else's shortlist id can't even confirm it exists.
 */
@Service
public class ShortlistService {

    private static final String PROVIDERS_QUERY =
            """
            SELECT sp.provider_id, sp.created_at AS added_at, p.npi_number, p.entity_type, p.first_name, p.last_name,
                   p.organization_name, pl.id AS location_id, pl.address_line_1, pl.address_line_2, pl.city,
                   pl.state_code, pl.postal_code, pl.phone, pl.latitude, pl.longitude, pl.coordinate_precision
            FROM shortlist_provider sp
            JOIN provider p ON p.id = sp.provider_id
            LEFT JOIN provider_location pl ON pl.provider_id = p.id AND pl.is_primary = TRUE
            WHERE sp.shortlist_id = ?
            ORDER BY sp.created_at DESC
            """;

    private final ShortlistRepository shortlistRepository;
    private final ShortlistProviderRepository shortlistProviderRepository;
    private final ProviderRepository providerRepository;
    private final JdbcTemplate jdbcTemplate;

    public ShortlistService(
            ShortlistRepository shortlistRepository,
            ShortlistProviderRepository shortlistProviderRepository,
            ProviderRepository providerRepository,
            JdbcTemplate jdbcTemplate) {
        this.shortlistRepository = shortlistRepository;
        this.shortlistProviderRepository = shortlistProviderRepository;
        this.providerRepository = providerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ShortlistDto> list(Long userId) {
        return shortlistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(shortlist -> new ShortlistDto(
                        shortlist.getId(),
                        shortlist.getName(),
                        shortlistProviderRepository.countByShortlistId(shortlist.getId()),
                        shortlist.getCreatedAt(),
                        shortlist.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ShortlistDto create(Long userId, String name) {
        Instant now = Instant.now();
        Shortlist saved = shortlistRepository.save(new Shortlist(userId, name.trim(), now, now));
        return new ShortlistDto(saved.getId(), saved.getName(), 0, saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public ShortlistDto rename(Long userId, Long shortlistId, String name) {
        Shortlist shortlist = requireOwnedShortlist(userId, shortlistId);
        shortlist.setName(name.trim());
        shortlist.setUpdatedAt(Instant.now());
        return new ShortlistDto(
                shortlist.getId(),
                shortlist.getName(),
                shortlistProviderRepository.countByShortlistId(shortlist.getId()),
                shortlist.getCreatedAt(),
                shortlist.getUpdatedAt());
    }

    @Transactional
    public void delete(Long userId, Long shortlistId) {
        Shortlist shortlist = requireOwnedShortlist(userId, shortlistId);
        // shortlist_provider rows cascade at the DB level (ON DELETE CASCADE, V11) -- providers
        // themselves are never touched, only this user's membership rows.
        shortlistRepository.delete(shortlist);
    }

    public ShortlistDetailDto getDetail(Long userId, Long shortlistId) {
        Shortlist shortlist = requireOwnedShortlist(userId, shortlistId);
        List<ShortlistProviderDto> providers = jdbcTemplate.query(
                PROVIDERS_QUERY,
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
                    return new ShortlistProviderDto(
                            rs.getLong("provider_id"),
                            rs.getTimestamp("added_at").toInstant(),
                            rs.getString("npi_number"),
                            rs.getString("entity_type"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("organization_name"),
                            location);
                },
                shortlist.getId());
        return new ShortlistDetailDto(shortlist.getId(), shortlist.getName(), providers, shortlist.getCreatedAt(), shortlist.getUpdatedAt());
    }

    @Transactional
    public void addProvider(Long userId, Long shortlistId, Long providerId) {
        Shortlist shortlist = requireOwnedShortlist(userId, shortlistId);
        if (!providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        // ON CONFLICT DO NOTHING, not check-then-insert: see SavedProviderService for why a
        // try/catch around a plain insert doesn't work inside a @Transactional method once the DB
        // constraint violation happens during flush.
        jdbcTemplate.update(
                "INSERT INTO shortlist_provider (shortlist_id, provider_id, created_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (shortlist_id, provider_id) DO NOTHING",
                shortlist.getId(),
                providerId,
                Timestamp.from(Instant.now()));
        shortlist.setUpdatedAt(Instant.now());
    }

    @Transactional
    public void removeProvider(Long userId, Long shortlistId, Long providerId) {
        Shortlist shortlist = requireOwnedShortlist(userId, shortlistId);
        shortlistProviderRepository.deleteByShortlistIdAndProviderId(shortlist.getId(), providerId);
        shortlist.setUpdatedAt(Instant.now());
    }

    private Shortlist requireOwnedShortlist(Long userId, Long shortlistId) {
        return shortlistRepository
                .findByIdAndUserId(shortlistId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shortlist not found."));
    }
}
