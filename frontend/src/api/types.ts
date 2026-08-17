export interface SpecialtyDto {
  code: string
  name: string
  description: string
}

/** Real, runtime-queried counts only -- never a hardcoded/marketing number. */
export interface CoverageDto {
  providerCount: number
  locationCount: number
  specialtyCount: number
  // Reference geography DocFit knows about (zip_geography) -- not a claim that provider data was
  // imported for all of it. See providerZipCount/providerCityCount for what was actually imported.
  geographyZipCount: number
  geographyCityCount: number
  geographyCountyCount: number
  geographySource: string | null
  providerZipCount: number
  providerCityCount: number
  sampleProviderAreas: string[]
  sampleProviderAreasTruncated: boolean
  lastImportCompletedAt: string | null
}

export interface InsuranceCarrierDto {
  id: number
  name: string
}

export interface PayerDto {
  id: number
  code: string
  name: string
  hasIntegratedPlans: boolean
}

export interface InsurancePlanDto {
  id: number
  payerId: number
  planName: string
  planType: string
}

/** Machine-readable evidence status. NO_EVIDENCE_FOUND never means "out of network" -- see docs/insurance-network-architecture.md. */
export type NetworkEvidenceStatusValue =
  | 'EVIDENCE_FOUND'
  | 'NO_EVIDENCE_FOUND'
  | 'SOURCE_UNAVAILABLE'
  | 'MATCH_AMBIGUOUS'
  | 'NOT_CHECKED'

export type NetworkEvidenceFreshnessValue = 'FRESH' | 'AGING' | 'STALE'

export interface NetworkEvidenceSummaryDto {
  status: NetworkEvidenceStatusValue
  freshness: NetworkEvidenceFreshnessValue | null
  planName: string | null
  networkName: string | null
  synthetic: boolean
  checkedAt: string | null
}

export interface NetworkEvidenceDetailDto {
  providerId: number
  planId: number
  planName: string
  networkName: string | null
  payerName: string
  status: NetworkEvidenceStatusValue
  freshness: NetworkEvidenceFreshnessValue | null
  matchedAddressLine1: string | null
  matchedCity: string | null
  matchedStateCode: string | null
  matchedPostalCode: string | null
  matchMethod: string | null
  sourceName: string | null
  sourceType: string | null
  synthetic: boolean
  checkedAt: string | null
  firstSeenAt: string | null
  limitations: string[]
}

export type ProviderEntityTypeValue = 'INDIVIDUAL' | 'ORGANIZATION'

/** Precise geocode -- a normal map pin is appropriate. */
export const PRECISE_COORDINATE_PRECISIONS = new Set(['EXACT', 'ADDRESS_GEOCODE'])

/** {@code coordinatePrecision} is truthful -- ZIP_CENTROID means an approximate ZIP-code lookup, never a real address geocode. */
export interface ProviderLocationDto {
  id: number
  addressLine1: string
  addressLine2: string | null
  city: string
  stateCode: string
  postalCode: string
  phone: string | null
  latitude: number | null
  longitude: number | null
  coordinatePrecision: string
  /** Null whenever no search origin applies (e.g. the saved-providers list) -- never a fabricated distance. */
  distanceMiles?: number | null
}

/** One provider per result, attached to its single nearest qualifying practice location (never repeated per office). */
export interface ProviderSearchResultDto {
  id: number
  npiNumber: string
  entityType: ProviderEntityTypeValue
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  taxonomyCode: string
  specialtyDisplayName: string
  location: ProviderLocationDto
  distanceMiles: number
  networkEvidence: NetworkEvidenceSummaryDto | null
  /** Total practice locations this provider has, not just the one shown here (CLAUDE.md "Multi-Location Discovery"). */
  locationCount: number
}

export interface ProviderSearchResponseDto {
  results: ProviderSearchResultDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  originLabel: string | null
}

export interface ProviderTaxonomyDto {
  taxonomyCode: string
  classification: string
  specialization: string | null
  displayName: string
  primaryTaxonomy: boolean
}

/** {@code location} is the selected/nearest location; {@code otherLocations} holds the provider's remaining offices, if any. */
export interface ProviderDetailDto {
  id: number
  npiNumber: string
  entityType: ProviderEntityTypeValue
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  location: ProviderLocationDto | null
  otherLocations: ProviderLocationDto[]
  distanceMiles: number | null
  taxonomies: ProviderTaxonomyDto[]
  importedAt: string | null
}

export interface ProviderNameSearchResultDto {
  id: number
  npiNumber: string
  entityType: ProviderEntityTypeValue
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  city: string
  stateCode: string
  specialtyDisplayName: string
}

export type SortOption = 'distance' | 'name' | 'name-desc'

export interface LocationSuggestionDto {
  // ZIP suggestions carry a specific zipCode; CITY suggestions (deduplicated across every ZIP that
  // shares the city) carry a null zipCode instead -- selecting one searches by city/state text.
  zipCode: string | null
  city: string | null
  stateCode: string
  label: string
  type: 'ZIP' | 'CITY'
}

export interface UserDto {
  id: number
  email: string
  displayName: string | null
  createdAt: string
}

export interface AuthResponseDto {
  accessToken: string
  expiresInSeconds: number
  user: UserDto
}

export interface SavedProviderDto {
  id: number
  savedAt: string
  providerId: number
  npiNumber: string
  entityType: ProviderEntityTypeValue
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  location: ProviderLocationDto | null
}

export interface ShortlistDto {
  id: number
  name: string
  providerCount: number
  createdAt: string
  updatedAt: string
}

export interface ShortlistProviderDto {
  providerId: number
  addedAt: string
  npiNumber: string
  entityType: ProviderEntityTypeValue
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  location: ProviderLocationDto | null
}

export interface ShortlistDetailDto {
  id: number
  name: string
  providers: ShortlistProviderDto[]
  createdAt: string
  updatedAt: string
}

export type ReportTypeValue =
  | 'WRONG_ADDRESS'
  | 'WRONG_PHONE_NUMBER'
  | 'PROVIDER_NOT_AT_LOCATION'
  | 'NAME_APPEARS_INCORRECT'
  | 'SPECIALTY_APPEARS_INCORRECT'
  | 'DUPLICATE_PROVIDER_OR_LOCATION'
  | 'INSURANCE_INFO_APPEARS_INCORRECT'
  | 'OTHER'

// ---------- Care Navigator ----------

export type NavigationStatusValue = 'SAVED' | 'TO_CONTACT' | 'CONTACTED' | 'VERIFYING_DETAILS' | 'SHORTLISTED' | 'ARCHIVED'

export const NAVIGATION_STATUS_LABELS: Record<NavigationStatusValue, string> = {
  SAVED: 'Saved',
  TO_CONTACT: 'To contact',
  CONTACTED: 'Contacted',
  VERIFYING_DETAILS: 'Verifying details',
  SHORTLISTED: 'Shortlisted',
  ARCHIVED: 'Archived',
}

export const NAVIGATION_STATUS_VALUES: NavigationStatusValue[] = [
  'SAVED',
  'TO_CONTACT',
  'CONTACTED',
  'VERIFYING_DETAILS',
  'SHORTLISTED',
  'ARCHIVED',
]

export interface NavigationStatusDto {
  providerId: number
  status: NavigationStatusValue
  updatedAt: string
}

export type VerificationTypeValue =
  | 'LOCATION'
  | 'PHONE'
  | 'ACCEPTING_NEW_PATIENTS'
  | 'INSURANCE_NETWORK'
  | 'APPOINTMENT_AVAILABILITY'
  | 'EXPECTED_COST'

export const VERIFICATION_TYPE_LABELS: Record<VerificationTypeValue, string> = {
  LOCATION: 'Confirm office location',
  PHONE: 'Confirm phone number',
  ACCEPTING_NEW_PATIENTS: 'Confirm accepting-new-patient status',
  INSURANCE_NETWORK: 'Confirm insurance/network status',
  APPOINTMENT_AVAILABILITY: 'Ask about appointment availability',
  EXPECTED_COST: 'Ask about expected cost',
}

export type VerificationItemStatusValue = 'NOT_STARTED' | 'NEEDS_CONFIRMATION' | 'CONFIRMED_BY_USER' | 'NOT_APPLICABLE'

export interface VerificationItemDto {
  verificationType: VerificationTypeValue
  status: VerificationItemStatusValue
  confirmedAt: string | null
  updatedAt: string | null
}

export interface ReminderDto {
  id: number
  title: string
  dueAt: string
  completedAt: string | null
  providerId: number | null
  providerName: string | null
  shortlistId: number | null
  shortlistName: string | null
  createdAt: string
}

export interface SavedPlanDto {
  id: number
  payerId: number
  payerName: string
  insurancePlanId: number
  planName: string
  createdAt: string
  updatedAt: string
}

export interface NavigatorProviderDto {
  providerId: number
  npiNumber: string
  entityType: ProviderEntityTypeValue
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  location: ProviderLocationDto | null
  status: NavigationStatusValue
  verificationCompleted: number
  verificationTotal: number
  networkEvidence: NetworkEvidenceSummaryDto | null
  nextAction: string
  savedAt: string
}

export interface NavigatorShortlistSummaryDto {
  id: number
  name: string
  providerCount: number
  toContactCount: number
  contactedCount: number
  createdAt: string
  updatedAt: string
}

export interface NavigatorDashboardDto {
  savedCount: number
  toContactCount: number
  verificationNeededCount: number
  providers: NavigatorProviderDto[]
  shortlists: NavigatorShortlistSummaryDto[]
  upcomingReminders: ReminderDto[]
  savedPlan: SavedPlanDto | null
}

export interface SavedSearchDto {
  id: number
  name: string | null
  specialtyCode: string
  specialtyName: string
  locationText: string | null
  latitude: number | null
  longitude: number | null
  radius: number
  sort: string
  createdAt: string
}
