package com.docfitai.backend.navigator;

/**
 * {@code CONFIRMED_BY_USER} means only that the user says they confirmed the item directly with
 * the provider/insurer -- it is never promoted into DocFit's own provider/network-evidence data
 * (CLAUDE.md "Checklist States", "User Confirmation Semantics"). The UI must render it as "Marked
 * confirmed by you", never "Verified".
 */
public enum VerificationItemStatus {
    NOT_STARTED,
    NEEDS_CONFIRMATION,
    CONFIRMED_BY_USER,
    NOT_APPLICABLE
}
