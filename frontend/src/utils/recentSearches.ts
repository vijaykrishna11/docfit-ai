/**
 * Recent searches, kept entirely in the browser via sessionStorage (CLAUDE.md "Search History --
 * Privacy First"). Never sent to DocFit AI's server and never persisted beyond the browser tab/
 * session -- this is a convenience for the current visit, distinct from the explicit, server-side
 * "Save this search" feature, which only ever exists because a signed-in user clicked it.
 */

const STORAGE_KEY = 'docfitai.recentSearches'
const MAX_ENTRIES = 5

export interface RecentSearchEntry {
  specialtyCode: string
  specialtyName: string
  /** Human-readable location the search resolved to (or the raw ZIP/city text) -- never lat/lng alone, which wouldn't be readable here. */
  locationLabel: string
  radius: number
  searchedAt: string
}

export function getRecentSearches(): RecentSearchEntry[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as RecentSearchEntry[]) : []
  } catch {
    return []
  }
}

function isSameSearch(a: RecentSearchEntry, b: Omit<RecentSearchEntry, 'searchedAt'>): boolean {
  return a.specialtyCode === b.specialtyCode && a.locationLabel === b.locationLabel && a.radius === b.radius
}

export function recordRecentSearch(entry: Omit<RecentSearchEntry, 'searchedAt'>): void {
  try {
    const existing = getRecentSearches().filter((item) => !isSameSearch(item, entry))
    const next = [{ ...entry, searchedAt: new Date().toISOString() }, ...existing].slice(0, MAX_ENTRIES)
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // sessionStorage can be unavailable (private browsing, quota) -- fail silently, non-critical.
  }
}

export function clearRecentSearches(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Non-critical.
  }
}
