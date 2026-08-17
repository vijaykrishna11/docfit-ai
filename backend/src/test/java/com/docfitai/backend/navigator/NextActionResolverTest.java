package com.docfitai.backend.navigator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure rule-based logic, no Spring context needed -- see CLAUDE.md "Next Action Explainability": deterministic, never AI-generated. */
class NextActionResolverTest {

    @Test
    void savedProviderIsToldToReviewIt() {
        assertThat(NextActionResolver.resolve(NavigationStatus.SAVED, Map.of())).isEqualTo("Review provider");
    }

    @Test
    void toContactProviderIsToldToContactTheOffice() {
        assertThat(NextActionResolver.resolve(NavigationStatus.TO_CONTACT, Map.of())).isEqualTo("Contact office");
    }

    @Test
    void contactedWithUncheckedInsuranceAsksToConfirmInsuranceFirst() {
        assertThat(NextActionResolver.resolve(NavigationStatus.CONTACTED, Map.of())).isEqualTo("Confirm insurance");
    }

    @Test
    void contactedWithInsuranceConfirmedButOtherItemsPendingAsksForRemainingDetails() {
        Map<VerificationType, VerificationItemStatus> verification = new EnumMap<>(VerificationType.class);
        verification.put(VerificationType.INSURANCE_NETWORK, VerificationItemStatus.CONFIRMED_BY_USER);
        assertThat(NextActionResolver.resolve(NavigationStatus.CONTACTED, verification)).isEqualTo("Confirm remaining details");
    }

    @Test
    void contactedWithEverythingResolvedSuggestsReviewingTheShortlist() {
        Map<VerificationType, VerificationItemStatus> verification = new EnumMap<>(VerificationType.class);
        for (VerificationType type : VerificationType.values()) {
            verification.put(type, VerificationItemStatus.CONFIRMED_BY_USER);
        }
        assertThat(NextActionResolver.resolve(NavigationStatus.CONTACTED, verification)).isEqualTo("Review your shortlist");
    }

    @Test
    void notApplicableCountsAsResolvedJustLikeConfirmed() {
        Map<VerificationType, VerificationItemStatus> verification = new EnumMap<>(VerificationType.class);
        for (VerificationType type : VerificationType.values()) {
            verification.put(type, VerificationItemStatus.NOT_APPLICABLE);
        }
        assertThat(NextActionResolver.resolve(NavigationStatus.CONTACTED, verification)).isEqualTo("Review your shortlist");
    }

    @Test
    void archivedNeverSuggestsContactingOrConfirming() {
        assertThat(NextActionResolver.resolve(NavigationStatus.ARCHIVED, Map.of())).isEqualTo("Review archived providers");
    }

    @Test
    void shortlistedSuggestsReviewingTheShortlist() {
        assertThat(NextActionResolver.resolve(NavigationStatus.SHORTLISTED, Map.of())).isEqualTo("Review your shortlist");
    }
}
