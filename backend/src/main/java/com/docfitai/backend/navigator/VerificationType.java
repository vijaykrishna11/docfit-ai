package com.docfitai.backend.navigator;

/**
 * Allowlisted administrative items a user can track as "still need to verify" for a provider
 * (CLAUDE.md "Verification Types"). Fixed set -- never an arbitrary string, so this can never
 * become a hidden clinical field.
 */
public enum VerificationType {
    LOCATION,
    PHONE,
    ACCEPTING_NEW_PATIENTS,
    INSURANCE_NETWORK,
    APPOINTMENT_AVAILABILITY,
    EXPECTED_COST
}
