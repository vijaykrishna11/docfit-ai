import { useState } from 'react'
import { Link } from 'react-router-dom'
import { clearRecentlyViewed, getRecentlyViewed, type RecentlyViewedEntry } from '../utils/recentlyViewed'
import { CloseIcon } from './icons'

function RecentlyViewed() {
  const [entries, setEntries] = useState<RecentlyViewedEntry[]>(getRecentlyViewed)

  if (entries.length === 0) {
    return null
  }

  function handleClear() {
    clearRecentlyViewed()
    setEntries([])
  }

  return (
    <section className="recently-viewed" aria-label="Recently viewed providers">
      <div className="recently-viewed-header">
        <h3>Recently viewed</h3>
        <button type="button" className="chip chip-clear" onClick={handleClear}>
          <CloseIcon width={12} height={12} />
          Clear
        </button>
      </div>
      <p className="field-hint">Kept only in this browser tab -- never sent to DocFit AI.</p>
      <ul className="recently-viewed-list">
        {entries.map((entry) => (
          <li key={entry.id}>
            <Link className="chip recently-viewed-chip" to={`/providers/${entry.id}`}>
              {entry.name}
              {entry.specialtyDisplayName && <span className="recently-viewed-specialty"> · {entry.specialtyDisplayName}</span>}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default RecentlyViewed
