package com.docfitai.backend.navigator.dto;

import java.util.List;

public record NavigatorDashboardDto(
        int savedCount,
        int toContactCount,
        int verificationNeededCount,
        List<NavigatorProviderDto> providers,
        List<NavigatorShortlistSummaryDto> shortlists,
        List<ReminderDto> upcomingReminders,
        SavedPlanDto savedPlan) {
}
