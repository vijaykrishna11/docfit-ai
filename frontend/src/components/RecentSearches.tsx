import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { clearRecentSearches, getRecentSearches, type RecentSearchEntry } from '../utils/recentSearches'
import { CloseIcon } from './icons'

function buildSearchUrl(entry: RecentSearchEntry): string {
  const params = new URLSearchParams({
    specialty: entry.specialtyCode,
    location: entry.locationLabel,
    radius: String(entry.radius),
    sort: 'distance',
    page: '0',
  })
  return `/?${params.toString()}#search-panel`
}

/** "Continue exploring" (CLAUDE.md "Homepage Recent State") -- never rendered when empty, so the homepage stays uncluttered for a first-time visitor. */
function RecentSearches() {
  const [entries, setEntries] = useState<RecentSearchEntry[]>(getRecentSearches)
  const navigate = useNavigate()

  if (entries.length === 0) {
    return null
  }

  function handleClear() {
    clearRecentSearches()
    setEntries([])
  }

  return (
    <section className="recently-viewed" aria-label="Recent searches">
      <div className="recently-viewed-header">
        <h3>Recent searches</h3>
        <button type="button" className="chip chip-clear" onClick={handleClear}>
          <CloseIcon width={12} height={12} />
          Clear
        </button>
      </div>
      <p className="field-hint">Kept only in this browser tab -- never sent to DocFit AI.</p>
      <ul className="recently-viewed-list">
        {entries.map((entry, index) => (
          <li key={`${entry.specialtyCode}-${entry.locationLabel}-${index}`}>
            <button type="button" className="chip recently-viewed-chip" onClick={() => navigate(buildSearchUrl(entry))}>
              {entry.specialtyName} · {entry.locationLabel} · {entry.radius} mi
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default RecentSearches
