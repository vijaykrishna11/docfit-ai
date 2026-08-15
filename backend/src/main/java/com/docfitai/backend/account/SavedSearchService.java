package com.docfitai.backend.account;

import com.docfitai.backend.account.dto.SaveSearchRequest;
import com.docfitai.backend.account.dto.SavedSearchDto;
import com.docfitai.backend.reference.Specialty;
import com.docfitai.backend.reference.SpecialtyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedSearchService {

    private final SavedSearchRepository savedSearchRepository;
    private final SpecialtyRepository specialtyRepository;

    public SavedSearchService(SavedSearchRepository savedSearchRepository, SpecialtyRepository specialtyRepository) {
        this.savedSearchRepository = savedSearchRepository;
        this.specialtyRepository = specialtyRepository;
    }

    public List<SavedSearchDto> list(Long userId) {
        return savedSearchRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedSearchDto save(Long userId, SaveSearchRequest request) {
        var specialty = specialtyRepository
                .findByCode(request.specialtyCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown specialty code."));

        BigDecimal latitude = request.latitude() != null ? BigDecimal.valueOf(request.latitude()) : null;
        BigDecimal longitude = request.longitude() != null ? BigDecimal.valueOf(request.longitude()) : null;

        SavedSearch saved = savedSearchRepository.save(new SavedSearch(
                userId,
                blankToNull(request.name()),
                specialty.getCode(),
                blankToNull(request.locationText()),
                latitude,
                longitude,
                request.radius(),
                request.sort(),
                Instant.now()));
        return toDto(saved);
    }

    @Transactional
    public SavedSearchDto rename(Long userId, Long id, String name) {
        SavedSearch search = savedSearchRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found."));
        search.setName(blankToNull(name));
        return toDto(search);
    }

    @Transactional
    public void remove(Long userId, Long id) {
        SavedSearch search = savedSearchRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found."));
        savedSearchRepository.delete(search);
    }

    private SavedSearchDto toDto(SavedSearch search) {
        String specialtyName = specialtyRepository
                .findByCode(search.getSpecialtyCode())
                .map(Specialty::getName)
                .orElse(search.getSpecialtyCode());
        return new SavedSearchDto(
                search.getId(),
                search.getName(),
                search.getSpecialtyCode(),
                specialtyName,
                search.getLocationText(),
                search.getLatitude(),
                search.getLongitude(),
                search.getRadius(),
                search.getSort(),
                search.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
