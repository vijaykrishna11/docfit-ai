import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { fetchSavedSearches, removeSavedSearch } from '../api/client'
import type { SavedSearchDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { BookmarkIcon, LocationIcon } from '../components/icons'

export function buildRunSearchUrl(search: SavedSearchDto): string {
  const params = new URLSearchParams()
  params.set('specialty', search.specialtyCode)
  params.set('radius', String(search.radius))
  params.set('sort', search.sort)
  params.set('page', '0')
  if (search.latitude != null && search.longitude != null) {
    params.set('lat', String(search.latitude))
    params.set('lng', String(search.longitude))
  } else if (search.locationText) {
    params.set('location', search.locationText)
  }
  return `/?${params.toString()}`
}

function SavedSearchesPage() {
  const navigate = useNavigate()
  const [searches, setSearches] = useState<SavedSearchDto[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    fetchSavedSearches()
      .then((results) => {
        if (!cancelled) setSearches(results)
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function handleRemove(id: number) {
    setSearches((previous) => previous.filter((search) => search.id !== id))
    try {
      await removeSavedSearch(id)
    } catch {
      // Best-effort local removal; a page refresh will re-sync with the server if this failed.
    }
  }

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <div>
          <h1>Saved searches</h1>
          <p className="results-subtext">
            Searches you've chosen to save. DocFit AI never saves a search automatically.
          </p>
        </div>

        {isLoading && <p className="results-heading-loading">Loading your saved searches…</p>}

        {!isLoading && searches.length === 0 && (
          <div className="state-panel empty-panel">
            <div className="empty-panel-icon">
              <BookmarkIcon width={22} height={22} />
            </div>
            <h3>No saved searches yet</h3>
            <p className="state-hint">
              After running a search, use &ldquo;Save this search&rdquo; in the results toolbar to add it here.
            </p>
            <Link className="primary-button" to="/#search-panel">
              Find providers
            </Link>
          </div>
        )}

        {!isLoading && searches.length > 0 && (
          <ul className="provider-list">
            {searches.map((search) => (
              <li key={search.id} className="provider-card">
                <div className="provider-card-top">
                  <div className="avatar" aria-hidden="true">
                    <LocationIcon width={18} height={18} />
                  </div>
                  <div className="provider-card-heading">
                    <h3>{search.name || search.specialtyName}</h3>
                    <span className="specialty-badge">{search.specialtyName}</span>
                  </div>
                </div>

                <div className="provider-card-details">
                  <p className="detail">
                    <LocationIcon width={16} height={16} />
                    <span>
                      {search.locationText ?? 'Your location'} &middot; {search.radius} miles
                    </span>
                  </p>
                  <p className="npi">Saved {new Date(search.createdAt).toLocaleDateString()}</p>
                </div>

                <div className="provider-card-actions">
                  <div className="provider-card-buttons">
                    <button type="button" className="ghost-button" onClick={() => navigate(buildRunSearchUrl(search))}>
                      Run search
                    </button>
                    <button type="button" className="ghost-button" onClick={() => handleRemove(search.id)}>
                      Remove
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>
      <Footer />
    </div>
  )
}

export default SavedSearchesPage
