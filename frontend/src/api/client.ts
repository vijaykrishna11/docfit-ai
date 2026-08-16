import type {
  AuthResponseDto,
  InsuranceCarrierDto,
  InsurancePlanDto,
  LocationSuggestionDto,
  NetworkEvidenceDetailDto,
  PayerDto,
  ProviderDetailDto,
  ProviderNameSearchResultDto,
  ProviderSearchResponseDto,
  SavedProviderDto,
  SavedSearchDto,
  SortOption,
  SpecialtyDto,
  UserDto,
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

// The access token lives here in memory only -- never localStorage/sessionStorage -- and is
// set by AuthContext whenever it changes (login, refresh, logout). refreshHandler is likewise
// registered by AuthContext so a 401 can trigger one silent refresh-and-retry without every
// call site needing to know about the auth flow.
let currentAccessToken: string | null = null
let refreshHandler: (() => Promise<string | null>) | null = null
// The refresh token is single-use/rotating (see AuthService.refresh) -- if two requests hit a
// 401 at once (e.g. two parallel authenticated calls right as the access token expires), each
// independently calling refreshHandler() would fire two concurrent /api/auth/refresh calls
// against the same still-valid cookie, doubling refresh traffic and leaving one of the two new
// sessions immediately orphaned. This memoizes the in-flight attempt so concurrent 401s share
// one refresh and its result, clearing once it settles so the next real 401 starts a fresh one.
let inFlightRefresh: Promise<string | null> | null = null

export function setAccessToken(token: string | null) {
  currentAccessToken = token
}

export function setRefreshHandler(handler: (() => Promise<string | null>) | null) {
  refreshHandler = handler
}

function refreshAccessTokenOnce(): Promise<string | null> {
  if (!refreshHandler) {
    return Promise.resolve(null)
  }
  if (!inFlightRefresh) {
    inFlightRefresh = refreshHandler().finally(() => {
      inFlightRefresh = null
    })
  }
  return inFlightRefresh
}

async function readServerMessage(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = await response.clone().json()
    if (body && typeof body === 'object' && 'message' in body) {
      const message = (body as { message?: unknown }).message
      if (typeof message === 'string' && message.trim().length > 0) {
        return message
      }
    }
  } catch {
    // Not JSON or empty body -- fall through to a generic message.
  }
  return undefined
}

interface RequestOptions {
  method?: string
  body?: unknown
  /** Skips the automatic 401 refresh-and-retry (used by the refresh call itself to avoid loops). */
  skipAuthRetry?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}, isRetry = false): Promise<T> {
  const headers = new Headers({ 'Content-Type': 'application/json' })
  if (currentAccessToken) {
    headers.set('Authorization', `Bearer ${currentAccessToken}`)
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      credentials: 'include',
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(UNREACHABLE_MESSAGE, null)
  }

  if (response.status === 401 && !isRetry && !options.skipAuthRetry && refreshHandler) {
    const refreshedToken = await refreshAccessTokenOnce()
    if (refreshedToken) {
      return request<T>(path, options, true)
    }
  }

  if (!response.ok) {
    const serverMessage = await readServerMessage(response);
    switch (response.status) {
      case 400:
        throw new ApiError(serverMessage ?? INVALID_REQUEST_MESSAGE, 400)
      case 401:
        throw new ApiError(serverMessage ?? 'Please sign in to continue.', 401)
      case 404:
        throw new ApiError(serverMessage ?? 'Not found.', 404)
      case 409:
        throw new ApiError(serverMessage ?? 'This already exists.', 409)
      case 429:
        throw new ApiError(serverMessage ?? 'Too many attempts. Please wait and try again.', 429)
      default:
        throw new ApiError(UNREACHABLE_MESSAGE, response.status)
    }
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function fetchSpecialties(): Promise<SpecialtyDto[]> {
  return request<SpecialtyDto[]>('/api/specialties')
}

/** @deprecated Legacy, purely informational carrier list. Superseded by fetchPayers()/fetchPayerPlans(). */
export function fetchInsuranceCarriers(): Promise<InsuranceCarrierDto[]> {
  return request<InsuranceCarrierDto[]>('/api/insurance-carriers')
}

export function fetchPayers(): Promise<PayerDto[]> {
  return request<PayerDto[]>('/api/insurance/payers')
}

export function fetchPayerPlans(payerId: number): Promise<InsurancePlanDto[]> {
  return request<InsurancePlanDto[]>(`/api/insurance/payers/${payerId}/plans`)
}

export function fetchProviderNetworkEvidence(
  providerId: number,
  planId: number,
  locationId?: number,
): Promise<NetworkEvidenceDetailDto> {
  const params = new URLSearchParams({ planId: String(planId) })
  if (locationId != null) {
    params.set('locationId', String(locationId))
  }
  return request<NetworkEvidenceDetailDto>(`/api/providers/${providerId}/network-evidence?${params.toString()}`)
}

export interface ProviderSearchParams {
  specialty: string
  location?: string
  lat?: number
  lng?: number
  radius: number
  sort: SortOption
  page: number
  planId?: number
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
  if (searchParams.planId != null) {
    params.set('planId', String(searchParams.planId))
  }
  return request<ProviderSearchResponseDto>(`/api/providers/search?${params.toString()}`)
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
  return request<ProviderDetailDto>(`/api/providers/${id}${query ? `?${query}` : ''}`)
}

export function fetchLocationSuggestions(query: string): Promise<LocationSuggestionDto[]> {
  const params = new URLSearchParams({ q: query })
  return request<LocationSuggestionDto[]>(`/api/locations/suggestions?${params.toString()}`)
}

export function searchProvidersByName(query: string): Promise<ProviderNameSearchResultDto[]> {
  const params = new URLSearchParams({ q: query })
  return request<ProviderNameSearchResultDto[]>(`/api/providers/by-name?${params.toString()}`)
}

// ---------- Auth ----------

export function registerAccount(email: string, password: string, displayName: string): Promise<AuthResponseDto> {
  return request<AuthResponseDto>('/api/auth/register', {
    method: 'POST',
    body: { email, password, displayName: displayName || undefined },
    skipAuthRetry: true,
  })
}

export function loginAccount(email: string, password: string): Promise<AuthResponseDto> {
  return request<AuthResponseDto>('/api/auth/login', {
    method: 'POST',
    body: { email, password },
    skipAuthRetry: true,
  })
}

export function refreshAccessToken(): Promise<AuthResponseDto> {
  return request<AuthResponseDto>('/api/auth/refresh', { method: 'POST', skipAuthRetry: true })
}

export function logoutAccount(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST', skipAuthRetry: true })
}

export function fetchCurrentUser(): Promise<UserDto> {
  return request<UserDto>('/api/auth/me')
}

export function updateDisplayName(displayName: string): Promise<UserDto> {
  return request<UserDto>('/api/auth/me', { method: 'PATCH', body: { displayName } })
}

export function deleteAccount(): Promise<void> {
  return request<void>('/api/auth/me', { method: 'DELETE' })
}

// ---------- Saved providers ----------

export function fetchSavedProviders(): Promise<SavedProviderDto[]> {
  return request<SavedProviderDto[]>('/api/saved-providers')
}

export function saveProvider(providerId: number): Promise<void> {
  return request<void>(`/api/saved-providers/${providerId}`, { method: 'POST' })
}

export function unsaveProvider(providerId: number): Promise<void> {
  return request<void>(`/api/saved-providers/${providerId}`, { method: 'DELETE' })
}

// ---------- Saved searches ----------

export interface SaveSearchParams {
  name?: string
  specialtyCode: string
  locationText?: string
  latitude?: number
  longitude?: number
  radius: number
  sort: string
}

export function fetchSavedSearches(): Promise<SavedSearchDto[]> {
  return request<SavedSearchDto[]>('/api/saved-searches')
}

export function saveSearch(params: SaveSearchParams): Promise<SavedSearchDto> {
  return request<SavedSearchDto>('/api/saved-searches', { method: 'POST', body: params })
}

export function renameSavedSearch(id: number, name: string): Promise<SavedSearchDto> {
  return request<SavedSearchDto>(`/api/saved-searches/${id}`, { method: 'PATCH', body: { name } })
}

export function removeSavedSearch(id: number): Promise<void> {
  return request<void>(`/api/saved-searches/${id}`, { method: 'DELETE' })
}
