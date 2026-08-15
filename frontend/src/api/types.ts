export interface SpecialtyDto {
  code: string
  name: string
}

export interface InsuranceCarrierDto {
  id: number
  name: string
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
