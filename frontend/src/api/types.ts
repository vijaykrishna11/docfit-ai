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
}

export type SortOption = 'distance' | 'name' | 'name-desc'

export interface LocationSuggestionDto {
  zipCode: string
  city: string
  stateCode: string
  label: string
}
