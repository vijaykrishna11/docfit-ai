/**
 * Recently-viewed providers, kept entirely in the browser via sessionStorage.
 * Never sent to the server and never persisted beyond the browser tab/session --
 * this is a convenience for the current visit, not a saved-search-style feature.
 */

const STORAGE_KEY = 'docfitai.recentlyViewed'
const MAX_ENTRIES = 5

export interface RecentlyViewedEntry {
  id: number
  name: string
  specialtyDisplayName: string | null
  viewedAt: string
}

export function getRecentlyViewed(): RecentlyViewedEntry[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as RecentlyViewedEntry[]) : []
  } catch {
    return []
  }
}

export function recordRecentlyViewed(entry: Omit<RecentlyViewedEntry, 'viewedAt'>): void {
  try {
    const existing = getRecentlyViewed().filter((item) => item.id !== entry.id)
    const next = [{ ...entry, viewedAt: new Date().toISOString() }, ...existing].slice(0, MAX_ENTRIES)
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // sessionStorage can be unavailable (private browsing, quota) -- fail silently, non-critical.
  }
}

export function clearRecentlyViewed(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Non-critical.
  }
}
