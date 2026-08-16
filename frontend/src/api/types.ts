export interface SpecialtyDto {
  code: string
  name: string
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

export interface ProviderSearchResultDto {
  id: number
  npiNumber: string
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  phone: string | null
  addressLine1: string
  addressLine2: string | null
  city: string
  stateCode: string
  postalCode: string
  taxonomyCode: string
  specialtyDisplayName: string
  distanceMiles: number
  networkEvidence: NetworkEvidenceSummaryDto | null
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

export interface ProviderDetailDto {
  id: number
  npiNumber: string
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  phone: string | null
  addressLine1: string
  addressLine2: string | null
  city: string
  stateCode: string
  postalCode: string
  distanceMiles: number | null
  taxonomies: ProviderTaxonomyDto[]
  importedAt: string | null
}

export interface ProviderNameSearchResultDto {
  id: number
  npiNumber: string
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  city: string
  stateCode: string
  specialtyDisplayName: string
}

export type SortOption = 'distance' | 'name' | 'name-desc'

export interface LocationSuggestionDto {
  zipCode: string
  city: string
  stateCode: string
  label: string
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
  firstName: string | null
  lastName: string | null
  organizationName: string | null
  phone: string | null
  addressLine1: string
  addressLine2: string | null
  city: string
  stateCode: string
  postalCode: string
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
