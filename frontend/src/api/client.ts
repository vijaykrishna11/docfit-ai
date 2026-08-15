import type {
  InsuranceCarrierDto,
  LocationSuggestionDto,
  ProviderDetailDto,
  ProviderSearchResponseDto,
  SortOption,
  SpecialtyDto,
} from './types'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'
const INVALID_REQUEST_MESSAGE = "We couldn't process this search. Check your location and search options."

/** Carries an HTTP status (when known) so callers can show request-appropriate copy instead of raw errors. */
export class ApiError extends Error {
  readonly status: number | null

  constructor(message: string, status: number | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function getJson<T>(path: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`)
  } catch {
    throw new ApiError(UNREACHABLE_MESSAGE, null)
  }

  if (!response.ok) {
    if (response.status === 400) {
      throw new ApiError(INVALID_REQUEST_MESSAGE, 400)
    }
    throw new ApiError(UNREACHABLE_MESSAGE, response.status)
  }

  return response.json() as Promise<T>
}

export function fetchSpecialties(): Promise<SpecialtyDto[]> {
  return getJson<SpecialtyDto[]>('/api/specialties')
}

export function fetchInsuranceCarriers(): Promise<InsuranceCarrierDto[]> {
  return getJson<InsuranceCarrierDto[]>('/api/insurance-carriers')
}

export interface ProviderSearchParams {
  specialty: string
  location?: string
  lat?: number
  lng?: number
  radius: number
  sort: SortOption
  page: number
}

const RESULTS_PAGE_SIZE = 20

export function searchProviders(searchParams: ProviderSearchParams): Promise<ProviderSearchResponseDto> {
  const params = new URLSearchParams({
    specialty: searchParams.specialty,
    radius: String(searchParams.radius),
    sort: searchParams.sort,
    page: String(searchParams.page),
    size: String(RESULTS_PAGE_SIZE),
  })
  if (searchParams.lat != null && searchParams.lng != null) {
    params.set('lat', String(searchParams.lat))
    params.set('lng', String(searchParams.lng))
  } else if (searchParams.location) {
    params.set('location', searchParams.location)
  }
  return getJson<ProviderSearchResponseDto>(`/api/providers/search?${params.toString()}`)
}

export interface ProviderDetailParams {
  location?: string
  lat?: number
  lng?: number
}

export function fetchProviderDetail(id: number, origin: ProviderDetailParams = {}): Promise<ProviderDetailDto> {
  const params = new URLSearchParams()
  if (origin.lat != null && origin.lng != null) {
    params.set('lat', String(origin.lat))
    params.set('lng', String(origin.lng))
  } else if (origin.location) {
    params.set('location', origin.location)
  }
  const query = params.toString()
  return getJson<ProviderDetailDto>(`/api/providers/${id}${query ? `?${query}` : ''}`)
}

export function fetchLocationSuggestions(query: string): Promise<LocationSuggestionDto[]> {
  const params = new URLSearchParams({ q: query })
  return getJson<LocationSuggestionDto[]>(`/api/locations/suggestions?${params.toString()}`)
}
