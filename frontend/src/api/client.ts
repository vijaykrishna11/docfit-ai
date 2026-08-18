import type {
  AuthResponseDto,
  CoverageDto,
  InsuranceCarrierDto,
  InsurancePlanDto,
  LocationSuggestionDto,
  NavigationStatusDto,
  NavigationStatusValue,
  NavigatorDashboardDto,
  NetworkEvidenceDetailDto,
  PayerDto,
  ProviderDetailDto,
  ProviderNameSearchResultDto,
  ProviderSearchResponseDto,
  ReminderDto,
  ReportTypeValue,
  SavedPlanDto,
  SavedProviderDto,
  SavedSearchDto,
  ShortlistDetailDto,
  ShortlistDto,
  SortOption,
  SpecialtyDto,
  UserDto,
  VerificationItemDto,
  VerificationItemStatusValue,
  VerificationTypeValue,
} from './types'

/**
 * Same-origin Render deployment (CLAUDE.md "Render Same-Origin Deployment"): Spring Boot serves
 * both the API and the built SPA from one origin, so the production build must call the API at
 * whatever origin is actually hosting the page -- never a hardcoded hostname. `VITE_API_BASE_URL`
 * remains a supported explicit override (e.g. for a future split frontend/backend topology); when
 * unset, dev defaults to the local backend dev server, and a production build defaults to
 * `window.location.origin` (same-origin, relative-equivalent) rather than `localhost:8080`, which
 * would be wrong for every real deployed environment.
 */
export function resolveApiBaseUrl(explicitBaseUrl: string | undefined, isProd: boolean, currentOrigin: string): string {
  if (explicitBaseUrl) {
    return explicitBaseUrl
  }
  return isProd ? currentOrigin : 'http://localhost:8080'
}

export const API_BASE_URL = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL, import.meta.env.PROD, window.location.origin)

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

/**
 * Deduped: also used by AuthContext's own initial-mount session restore, not just the 401-retry
 * path below -- React StrictMode's dev-only double-effect-invocation would otherwise fire two
 * concurrent /api/auth/refresh calls on first mount, racing the single-use refresh-token rotation
 * and sometimes leaving a freshly-registered/logged-in user logged out on the very next full page
 * load (found via Playwright: a full `page.goto()` to any protected route right after
 * registration reproduced this deterministically).
 */
export function refreshAccessTokenOnce(): Promise<string | null> {
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

export function fetchCoverage(): Promise<CoverageDto> {
  return request<CoverageDto>('/api/discovery/coverage')
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

/** Practical-fit filters: all optional, all off (undefined) by default -- never a default that silently narrows results. */
export interface PracticalFitFilters {
  providerType?: 'INDIVIDUAL' | 'ORGANIZATION'
  hasPhone?: boolean
  preciseLocationOnly?: boolean
  networkEvidenceFound?: boolean
  multipleLocations?: boolean
}

export interface ProviderSearchParams extends PracticalFitFilters {
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
  if (searchParams.providerType) {
    params.set('providerType', searchParams.providerType)
  }
  if (searchParams.hasPhone) {
    params.set('hasPhone', 'true')
  }
  if (searchParams.preciseLocationOnly) {
    params.set('preciseLocationOnly', 'true')
  }
  if (searchParams.networkEvidenceFound) {
    params.set('networkEvidenceFound', 'true')
  }
  if (searchParams.multipleLocations) {
    params.set('multipleLocations', 'true')
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

// ---------- Shortlists ----------

export function fetchShortlists(): Promise<ShortlistDto[]> {
  return request<ShortlistDto[]>('/api/account/shortlists')
}

export function createShortlist(name: string): Promise<ShortlistDto> {
  return request<ShortlistDto>('/api/account/shortlists', { method: 'POST', body: { name } })
}

export function fetchShortlistDetail(id: number): Promise<ShortlistDetailDto> {
  return request<ShortlistDetailDto>(`/api/account/shortlists/${id}`)
}

export function renameShortlist(id: number, name: string): Promise<ShortlistDto> {
  return request<ShortlistDto>(`/api/account/shortlists/${id}`, { method: 'PATCH', body: { name } })
}

export function deleteShortlist(id: number): Promise<void> {
  return request<void>(`/api/account/shortlists/${id}`, { method: 'DELETE' })
}

export function addProviderToShortlist(shortlistId: number, providerId: number): Promise<void> {
  return request<void>(`/api/account/shortlists/${shortlistId}/providers/${providerId}`, { method: 'POST' })
}

export function removeProviderFromShortlist(shortlistId: number, providerId: number): Promise<void> {
  return request<void>(`/api/account/shortlists/${shortlistId}/providers/${providerId}`, { method: 'DELETE' })
}

// ---------- Directory-data correction reports ----------

export interface SubmitReportParams {
  reportType: ReportTypeValue
  providerLocationId?: number
  comment?: string
}

export function submitProviderDataReport(providerId: number, params: SubmitReportParams): Promise<{ id: number }> {
  return request<{ id: number }>(`/api/providers/${providerId}/reports`, { method: 'POST', body: params })
}

// ---------- Care Navigator ----------

export function fetchNavigatorDashboard(): Promise<NavigatorDashboardDto> {
  return request<NavigatorDashboardDto>('/api/account/navigator')
}

export function updateNavigationStatus(providerId: number, status: NavigationStatusValue): Promise<NavigationStatusDto> {
  return request<NavigationStatusDto>(`/api/account/providers/${providerId}/navigation-status`, {
    method: 'PUT',
    body: { status },
  })
}

export function fetchVerificationItems(providerId: number): Promise<VerificationItemDto[]> {
  return request<VerificationItemDto[]>(`/api/account/providers/${providerId}/verification-items`)
}

export function updateVerificationItem(
  providerId: number,
  verificationType: VerificationTypeValue,
  status: VerificationItemStatusValue,
  providerLocationId?: number,
): Promise<VerificationItemDto> {
  return request<VerificationItemDto>(`/api/account/providers/${providerId}/verification-items/${verificationType}`, {
    method: 'PUT',
    body: { status, providerLocationId },
  })
}

export function fetchReminders(): Promise<ReminderDto[]> {
  return request<ReminderDto[]>('/api/account/reminders')
}

export interface CreateReminderParams {
  title: string
  dueAt: string
  providerId?: number
  shortlistId?: number
}

export function createReminder(params: CreateReminderParams): Promise<ReminderDto> {
  return request<ReminderDto>('/api/account/reminders', { method: 'POST', body: params })
}

export function setReminderCompleted(id: number, completed: boolean): Promise<void> {
  return request<void>(`/api/account/reminders/${id}`, { method: 'PATCH', body: { completed } })
}

export function deleteReminder(id: number): Promise<void> {
  return request<void>(`/api/account/reminders/${id}`, { method: 'DELETE' })
}

export function fetchSavedPlan(): Promise<SavedPlanDto | null> {
  return request<SavedPlanDto | null>('/api/account/saved-plan')
}

export function saveSavedPlan(insurancePlanId: number): Promise<SavedPlanDto> {
  return request<SavedPlanDto>('/api/account/saved-plan', { method: 'PUT', body: { insurancePlanId } })
}

export function removeSavedPlan(): Promise<void> {
  return request<void>('/api/account/saved-plan', { method: 'DELETE' })
}

/** Downloads the caller's full data export as a JSON file (CLAUDE.md "User Data Download"). */
export async function downloadUserDataExport(): Promise<void> {
  const data = await request<unknown>('/api/account/export')
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = 'docfit-ai-data-export.json'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } finally {
    URL.revokeObjectURL(url)
  }
}
