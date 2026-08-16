import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { SavedProviderDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { BadgeIcon, BookmarkIcon, DirectionsIcon, LocationIcon, PhoneIcon } from '../components/icons'
import ShareSelectedProvidersButton from '../components/ShareSelectedProvidersButton'
import { useSavedProviders } from '../context/SavedProvidersContext'
import { directionsUrl, formattedAddress, initialsFor, providerDisplayName, telHref } from '../utils/providerDisplay'

function SavedProvidersPage() {
  const { savedProviders, isLoading, toggleSaved } = useSavedProviders()
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())

  function toggleSelection(providerId: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(providerId)) next.delete(providerId)
      else next.add(providerId)
      return next
    })
  }

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <div className="shortlist-detail-header">
          <div>
            <h1>Saved providers</h1>
            <p className="results-subtext">Providers you've chosen to keep track of. Only you can see this list.</p>
          </div>
          {savedProviders.length > 0 && (
            <div className="shortlist-detail-actions">
              <ShareSelectedProvidersButton selectedIds={Array.from(selectedIds)} />
            </div>
          )}
        </div>

        {isLoading && <p className="results-heading-loading">Loading your saved providers…</p>}

        {!isLoading && savedProviders.length === 0 && (
          <div className="state-panel empty-panel">
            <div className="empty-panel-icon">
              <BookmarkIcon width={22} height={22} />
            </div>
            <h3>Nothing saved yet</h3>
            <p className="state-hint">
              When you find a provider you like, tap the heart icon on their card or profile to add it here.
            </p>
            <Link className="primary-button" to="/#search-panel">
              Find providers
            </Link>
          </div>
        )}

        {!isLoading && savedProviders.length > 0 && (
          <ul className="provider-list">
            {savedProviders.map((provider) => (
              <SavedProviderCard
                key={provider.id}
                provider={provider}
                selected={selectedIds.has(provider.providerId)}
                onToggleSelect={() => toggleSelection(provider.providerId)}
                onRemove={() => toggleSaved(provider.providerId)}
              />
            ))}
          </ul>
        )}
      </main>
      <Footer />
    </div>
  )
}

function SavedProviderCard({
  provider,
  selected,
  onToggleSelect,
  onRemove,
}: {
  provider: SavedProviderDto
  selected: boolean
  onToggleSelect: () => void
  onRemove: () => void
}) {
  const name = providerDisplayName(provider)
  const location = provider.location

  return (
    <li className={`provider-card${selected ? ' is-selected' : ''}`}>
      <div className="provider-card-top">
        <label className="compare-checkbox" style={{ marginRight: 4 }}>
          <input type="checkbox" checked={selected} onChange={onToggleSelect} aria-label={`Select ${name} to share`} />
          <span className="compare-checkbox-box" aria-hidden="true" />
        </label>
        <div className="avatar" aria-hidden="true">
          {initialsFor(name)}
        </div>
        <div className="provider-card-heading">
          <h3>{name}</h3>
        </div>
      </div>

      <div className="provider-card-details">
        {location && (
          <p className="detail">
            <LocationIcon width={16} height={16} />
            <span>
              {formattedAddress(location).line1}
              <br />
              {formattedAddress(location).line2}
            </span>
          </p>
        )}
        {location?.phone && (
          <p className="detail">
            <PhoneIcon width={16} height={16} />
            <span>{location.phone}</span>
          </p>
        )}
        <p className="npi">
          <BadgeIcon width={14} height={14} />
          <span>NPI {provider.npiNumber}</span>
        </p>
      </div>

      <div className="provider-card-actions">
        <div className="provider-card-buttons">
          {location?.phone && (
            <a className="ghost-button" href={telHref(location.phone)}>
              <PhoneIcon width={14} height={14} />
              Call
            </a>
          )}
          {location && (
            <a className="ghost-button" href={directionsUrl(location)} target="_blank" rel="noopener noreferrer">
              <DirectionsIcon width={14} height={14} />
              Directions
            </a>
          )}
          <Link className="ghost-button" to={`/providers/${provider.providerId}`}>
            View details
          </Link>
          <button type="button" className="ghost-button" onClick={onRemove}>
            Remove
          </button>
        </div>
      </div>
    </li>
  )
}

export default SavedProvidersPage
