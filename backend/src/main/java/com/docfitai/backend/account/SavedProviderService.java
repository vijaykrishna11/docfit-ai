package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.SavedProviderDto;
import com.docfitai.backend.provider.ProviderRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedProviderService {

    private static final String LIST_QUERY =
            """
            SELECT sp.id, sp.created_at, p.id AS provider_id, p.npi_number, p.first_name, p.last_name,
                   p.organization_name, p.phone, p.address_line_1, p.address_line_2, p.city, p.state_code,
                   p.postal_code
            FROM saved_provider sp
            JOIN provider p ON p.id = sp.provider_id
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
                (rs, rowNum) -> new SavedProviderDto(
                        rs.getLong("id"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("provider_id"),
                        rs.getString("npi_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("organization_name"),
                        rs.getString("phone"),
                        rs.getString("address_line_1"),
                        rs.getString("address_line_2"),
                        rs.getString("city"),
                        rs.getString("state_code"),
                        rs.getString("postal_code")),
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
