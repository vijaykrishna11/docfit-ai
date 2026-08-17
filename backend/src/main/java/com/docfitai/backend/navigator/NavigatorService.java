package com.docfitai.backend.navigator;

import com.docfitai.backend.account.Shortlist;
import com.docfitai.backend.account.ShortlistRepository;
import com.docfitai.backend.account.SavedProviderService;
import com.docfitai.backend.account.dto.SavedProviderDto;
import com.docfitai.backend.insurance.dto.NetworkEvidenceSummaryDto;
import com.docfitai.backend.insurance.evidence.NetworkEvidenceService;
import com.docfitai.backend.navigator.dto.NavigationStatusDto;
import com.docfitai.backend.navigator.dto.NavigatorDashboardDto;
import com.docfitai.backend.navigator.dto.NavigatorProviderDto;
import com.docfitai.backend.navigator.dto.NavigatorShortlistSummaryDto;
import com.docfitai.backend.navigator.dto.SavedPlanDto;
import com.docfitai.backend.navigator.dto.VerificationItemDto;
import com.docfitai.backend.provider.ProviderRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aggregates a signed-in user's own administrative navigation state -- never provider quality,
 * never another user's data (CLAUDE.md "Care Navigator Dashboard" / "Authorization Integration
 * Tests"). Every list here is bounded by what one person can realistically save/shortlist, so
 * batched queries (not per-provider round trips) keep this a small, fixed number of round trips
 * regardless of how many providers are saved (CLAUDE.md "Performance": "Navigator should not
 * perform one request per saved provider").
 */
@Service
public class NavigatorService {

    private static final String SHORTLIST_STATUS_AGGREGATE =
            """
            SELECT sp.shortlist_id, un.status, COUNT(*) AS cnt
            FROM shortlist_provider sp
            JOIN provider_shortlist s ON s.id = sp.shortlist_id AND s.user_id = ?
            LEFT JOIN user_provider_navigation un ON un.provider_id = sp.provider_id AND un.user_id = ?
            GROUP BY sp.shortlist_id, un.status
            """;

    private final SavedProviderService savedProviderService;
    private final ProviderNavigationRepository navigationRepository;
    private final ProviderVerificationItemRepository verificationItemRepository;
    private final ProviderRepository providerRepository;
    private final ShortlistRepository shortlistRepository;
    private final NetworkEvidenceService networkEvidenceService;
    private final SavedPlanService savedPlanService;
    private final ReminderService reminderService;
    private final JdbcTemplate jdbcTemplate;

    public NavigatorService(
            SavedProviderService savedProviderService,
            ProviderNavigationRepository navigationRepository,
            ProviderVerificationItemRepository verificationItemRepository,
            ProviderRepository providerRepository,
            ShortlistRepository shortlistRepository,
            NetworkEvidenceService networkEvidenceService,
            SavedPlanService savedPlanService,
            ReminderService reminderService,
            JdbcTemplate jdbcTemplate) {
        this.savedProviderService = savedProviderService;
        this.navigationRepository = navigationRepository;
        this.verificationItemRepository = verificationItemRepository;
        this.providerRepository = providerRepository;
        this.shortlistRepository = shortlistRepository;
        this.networkEvidenceService = networkEvidenceService;
        this.savedPlanService = savedPlanService;
        this.reminderService = reminderService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public NavigatorDashboardDto getDashboard(Long userId) {
        List<SavedProviderDto> savedProviders = savedProviderService.list(userId);
        List<Long> providerIds = savedProviders.stream().map(SavedProviderDto::providerId).toList();

        Map<Long, NavigationStatus> statusByProvider = new HashMap<>();
        for (ProviderNavigation navigation : navigationRepository.findByUserIdAndProviderIdIn(userId, providerIds)) {
            statusByProvider.put(navigation.getProviderId(), navigation.getStatus());
        }

        Map<Long, Map<VerificationType, VerificationItemStatus>> verificationByProvider = new HashMap<>();
        for (ProviderVerificationItem item : verificationItemRepository.findByUserIdAndProviderIdIn(userId, providerIds)) {
            verificationByProvider
                    .computeIfAbsent(item.getProviderId(), key -> new EnumMap<>(VerificationType.class))
                    .put(item.getVerificationType(), item.getStatus());
        }

        SavedPlanDto savedPlan = savedPlanService.get(userId);
        Map<Long, NetworkEvidenceSummaryDto> evidenceByProvider = Map.of();
        if (savedPlan != null) {
            Map<Long, Long> providerLocations = new HashMap<>();
            for (SavedProviderDto saved : savedProviders) {
                if (saved.location() != null) {
                    providerLocations.put(saved.providerId(), saved.location().id());
                }
            }
            evidenceByProvider = networkEvidenceService.summarizeForProviders(providerLocations, savedPlan.insurancePlanId());
        }

        int toContactCount = 0;
        int verificationNeededCount = 0;
        List<NavigatorProviderDto> providers = new java.util.ArrayList<>();
        for (SavedProviderDto saved : savedProviders) {
            NavigationStatus status = statusByProvider.getOrDefault(saved.providerId(), NavigationStatus.SAVED);
            Map<VerificationType, VerificationItemStatus> verification =
                    verificationByProvider.getOrDefault(saved.providerId(), Map.of());
            int completed = (int) java.util.Arrays.stream(VerificationType.values())
                    .filter(type -> isResolved(verification.get(type)))
                    .count();
            if (status == NavigationStatus.TO_CONTACT) {
                toContactCount++;
            }
            if (completed < VerificationType.values().length) {
                verificationNeededCount++;
            }
            providers.add(new NavigatorProviderDto(
                    saved.providerId(),
                    saved.npiNumber(),
                    saved.entityType(),
                    saved.firstName(),
                    saved.lastName(),
                    saved.organizationName(),
                    saved.location(),
                    status,
                    completed,
                    VerificationType.values().length,
                    evidenceByProvider.get(saved.providerId()),
                    NextActionResolver.resolve(status, verification),
                    saved.savedAt()));
        }

        return new NavigatorDashboardDto(
                savedProviders.size(),
                toContactCount,
                verificationNeededCount,
                providers,
                buildShortlistSummaries(userId),
                reminderService.upcoming(userId, 10),
                savedPlan);
    }

    @Transactional
    public NavigationStatusDto updateStatus(Long userId, Long providerId, NavigationStatus status) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        // Setting a status implies the user is considering this provider -- ensure it's also on
        // the plain saved-providers list (idempotent; CLAUDE.md "Navigation Status Database").
        savedProviderService.save(userId, providerId);
        Instant now = Instant.now();
        ProviderNavigation navigation = navigationRepository
                .findByUserIdAndProviderId(userId, providerId)
                .map(existing -> {
                    existing.setStatus(status);
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> new ProviderNavigation(userId, providerId, status, now, now));
        ProviderNavigation savedNavigation = navigationRepository.save(navigation);
        return new NavigationStatusDto(providerId, savedNavigation.getStatus(), savedNavigation.getUpdatedAt());
    }

    public List<VerificationItemDto> getVerificationItems(Long userId, Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        Map<VerificationType, ProviderVerificationItem> existing = new EnumMap<>(VerificationType.class);
        for (ProviderVerificationItem item : verificationItemRepository.findByUserIdAndProviderId(userId, providerId)) {
            existing.put(item.getVerificationType(), item);
        }
        List<VerificationItemDto> result = new java.util.ArrayList<>();
        for (VerificationType type : VerificationType.values()) {
            ProviderVerificationItem item = existing.get(type);
            result.add(item == null
                    ? new VerificationItemDto(type, VerificationItemStatus.NOT_STARTED, null, null)
                    : new VerificationItemDto(type, item.getStatus(), item.getConfirmedAt(), item.getUpdatedAt()));
        }
        return result;
    }

    @Transactional
    public VerificationItemDto updateVerificationItem(
            Long userId, Long providerId, VerificationType type, VerificationItemStatus status, Long providerLocationId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        Instant now = Instant.now();
        Instant confirmedAt = status == VerificationItemStatus.CONFIRMED_BY_USER ? now : null;
        ProviderVerificationItem item = verificationItemRepository
                .findByUserIdAndProviderIdAndVerificationType(userId, providerId, type)
                .map(existing -> {
                    existing.setStatus(status);
                    existing.setConfirmedAt(confirmedAt);
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> new ProviderVerificationItem(userId, providerId, providerLocationId, type, status, confirmedAt, now));
        ProviderVerificationItem saved = verificationItemRepository.save(item);
        return new VerificationItemDto(type, saved.getStatus(), saved.getConfirmedAt(), saved.getUpdatedAt());
    }

    private List<NavigatorShortlistSummaryDto> buildShortlistSummaries(Long userId) {
        List<Shortlist> shortlists = shortlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (shortlists.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> providerCounts = new HashMap<>();
        Map<Long, Long> toContactCounts = new HashMap<>();
        Map<Long, Long> contactedCounts = new HashMap<>();
        jdbcTemplate.query(
                SHORTLIST_STATUS_AGGREGATE,
                rs -> {
                    long shortlistId = rs.getLong("shortlist_id");
                    long count = rs.getLong("cnt");
                    String status = rs.getString("status");
                    providerCounts.merge(shortlistId, count, Long::sum);
                    if (NavigationStatus.TO_CONTACT.name().equals(status)) {
                        toContactCounts.merge(shortlistId, count, Long::sum);
                    } else if (NavigationStatus.CONTACTED.name().equals(status)) {
                        contactedCounts.merge(shortlistId, count, Long::sum);
                    }
                },
                userId,
                userId);
        return shortlists.stream()
                .map(shortlist -> new NavigatorShortlistSummaryDto(
                        shortlist.getId(),
                        shortlist.getName(),
                        providerCounts.getOrDefault(shortlist.getId(), 0L),
                        toContactCounts.getOrDefault(shortlist.getId(), 0L),
                        contactedCounts.getOrDefault(shortlist.getId(), 0L),
                        shortlist.getCreatedAt(),
                        shortlist.getUpdatedAt()))
                .toList();
    }

    private static boolean isResolved(VerificationItemStatus status) {
        return status == VerificationItemStatus.CONFIRMED_BY_USER || status == VerificationItemStatus.NOT_APPLICABLE;
    }
}
