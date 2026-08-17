package com.docfitai.backend.navigator;

import java.util.Map;

/**
 * Deterministic, rule-based "what's next" label for a saved provider (CLAUDE.md "Next Action" /
 * "Next Action Explainability": "no AI needed... it is rule-based workflow"). Every rule reads
 * only the user's own status and checklist choices -- never provider quality, ratings, or any
 * clinical signal.
 */
public final class NextActionResolver {

    private NextActionResolver() {
    }

    public static String resolve(NavigationStatus status, Map<VerificationType, VerificationItemStatus> verification) {
        if (status == NavigationStatus.ARCHIVED) {
            return "Review archived providers";
        }
        if (status == NavigationStatus.SAVED) {
            return "Review provider";
        }
        if (status == NavigationStatus.TO_CONTACT) {
            return "Contact office";
        }
        if (status == NavigationStatus.CONTACTED) {
            if (!isConfirmed(verification, VerificationType.INSURANCE_NETWORK)) {
                return "Confirm insurance";
            }
            if (!allResolved(verification)) {
                return "Confirm remaining details";
            }
            return "Review your shortlist";
        }
        if (status == NavigationStatus.VERIFYING_DETAILS) {
            return allResolved(verification) ? "Review your shortlist" : "Confirm remaining details";
        }
        // SHORTLISTED
        return "Review your shortlist";
    }

    private static boolean isConfirmed(Map<VerificationType, VerificationItemStatus> verification, VerificationType type) {
        VerificationItemStatus itemStatus = verification.get(type);
        return itemStatus == VerificationItemStatus.CONFIRMED_BY_USER || itemStatus == VerificationItemStatus.NOT_APPLICABLE;
    }

    private static boolean allResolved(Map<VerificationType, VerificationItemStatus> verification) {
        for (VerificationType type : VerificationType.values()) {
            if (!isConfirmed(verification, type)) {
                return false;
            }
        }
        return true;
    }
}
