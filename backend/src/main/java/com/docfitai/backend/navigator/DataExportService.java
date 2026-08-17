package com.docfitai.backend.navigator;

import com.docfitai.backend.account.AppUser;
import com.docfitai.backend.account.AppUserRepository;
import com.docfitai.backend.account.SavedProviderService;
import com.docfitai.backend.account.SavedSearchService;
import com.docfitai.backend.account.ShortlistService;
import com.docfitai.backend.account.dto.ShortlistDetailDto;
import com.docfitai.backend.account.dto.ShortlistDto;
import com.docfitai.backend.navigator.dto.AccountExportDto;
import com.docfitai.backend.navigator.dto.NavigationStatusDto;
import com.docfitai.backend.navigator.dto.UserDataExportDto;
import com.docfitai.backend.navigator.dto.VerificationItemExportDto;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the full "download my DocFit data" export for one authenticated user (CLAUDE.md "User
 * Data Download"). Only ever reads data scoped to the caller's own userId -- no endpoint here
 * accepts a user id from the client (CLAUDE.md "Data Export Security").
 */
@Service
public class DataExportService {

    private final AppUserRepository appUserRepository;
    private final SavedProviderService savedProviderService;
    private final ShortlistService shortlistService;
    private final SavedSearchService savedSearchService;
    private final SavedPlanService savedPlanService;
    private final ProviderNavigationRepository navigationRepository;
    private final ProviderVerificationItemRepository verificationItemRepository;
    private final ReminderService reminderService;

    public DataExportService(
            AppUserRepository appUserRepository,
            SavedProviderService savedProviderService,
            ShortlistService shortlistService,
            SavedSearchService savedSearchService,
            SavedPlanService savedPlanService,
            ProviderNavigationRepository navigationRepository,
            ProviderVerificationItemRepository verificationItemRepository,
            ReminderService reminderService) {
        this.appUserRepository = appUserRepository;
        this.savedProviderService = savedProviderService;
        this.shortlistService = shortlistService;
        this.savedSearchService = savedSearchService;
        this.savedPlanService = savedPlanService;
        this.navigationRepository = navigationRepository;
        this.verificationItemRepository = verificationItemRepository;
        this.reminderService = reminderService;
    }

    public UserDataExportDto export(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        List<ShortlistDetailDto> shortlists = shortlistService.list(userId).stream()
                .map(ShortlistDto::id)
                .map(id -> shortlistService.getDetail(userId, id))
                .toList();

        List<NavigationStatusDto> navigation = navigationRepository.findByUserId(userId).stream()
                .map(nav -> new NavigationStatusDto(nav.getProviderId(), nav.getStatus(), nav.getUpdatedAt()))
                .toList();

        List<VerificationItemExportDto> verificationItems = verificationItemRepository.findByUserId(userId).stream()
                .map(item -> new VerificationItemExportDto(
                        item.getProviderId(), item.getVerificationType(), item.getStatus(), item.getConfirmedAt(), item.getUpdatedAt()))
                .toList();

        return new UserDataExportDto(
                Instant.now(),
                new AccountExportDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt()),
                savedProviderService.list(userId),
                shortlists,
                savedSearchService.list(userId),
                savedPlanService.get(userId),
                navigation,
                verificationItems,
                reminderService.list(userId));
    }
}
