package com.docfitai.backend.navigator.dto;

import com.docfitai.backend.account.dto.SavedProviderDto;
import com.docfitai.backend.account.dto.SavedSearchDto;
import com.docfitai.backend.account.dto.ShortlistDetailDto;
import java.time.Instant;
import java.util.List;

/**
 * Everything this user owns, as one JSON download (CLAUDE.md "User Data Download" / "Data Export
 * Format"). Never includes {@code passwordHash}, refresh tokens, or any other user's data
 * (CLAUDE.md "Data Export Security" / "Data Export API").
 */
public record UserDataExportDto(
        Instant generatedAt,
        AccountExportDto account,
        List<SavedProviderDto> savedProviders,
        List<ShortlistDetailDto> shortlists,
        List<SavedSearchDto> savedSearches,
        SavedPlanDto savedPlan,
        List<NavigationStatusDto> navigation,
        List<VerificationItemExportDto> verificationItems,
        List<ReminderDto> reminders) {
}
