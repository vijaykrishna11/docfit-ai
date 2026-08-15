import { Link } from 'react-router-dom'
import type { SavedProviderDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { BadgeIcon, BookmarkIcon, DirectionsIcon, LocationIcon, PhoneIcon } from '../components/icons'
import { useSavedProviders } from '../context/SavedProvidersContext'
import { directionsUrl, formattedAddress, initialsFor, providerDisplayName, telHref } from '../utils/providerDisplay'

function SavedProvidersPage() {
  const { savedProviders, isLoading, toggleSaved } = useSavedProviders()

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <div>
          <h1>Saved providers</h1>
          <p className="results-subtext">Providers you've chosen to keep track of. Only you can see this list.</p>
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
              <SavedProviderCard key={provider.id} provider={provider} onRemove={() => toggleSaved(provider.providerId)} />
            ))}
          </ul>
        )}
      </main>
      <Footer />
    </div>
  )
}

function SavedProviderCard({ provider, onRemove }: { provider: SavedProviderDto; onRemove: () => void }) {
  const name = providerDisplayName(provider)
  const { line1, line2 } = formattedAddress(provider)

  return (
    <li className="provider-card">
      <div className="provider-card-top">
        <div className="avatar" aria-hidden="true">
          {initialsFor(name)}
        </div>
        <div className="provider-card-heading">
          <h3>{name}</h3>
        </div>
      </div>

      <div className="provider-card-details">
        <p className="detail">
          <LocationIcon width={16} height={16} />
          <span>
            {line1}
            <br />
            {line2}
          </span>
        </p>
        {provider.phone && (
          <p className="detail">
            <PhoneIcon width={16} height={16} />
            <span>{provider.phone}</span>
          </p>
        )}
        <p className="npi">
          <BadgeIcon width={14} height={14} />
          <span>NPI {provider.npiNumber}</span>
        </p>
      </div>

      <div className="provider-card-actions">
        <div className="provider-card-buttons">
          {provider.phone && (
            <a className="ghost-button" href={telHref(provider.phone)}>
              <PhoneIcon width={14} height={14} />
              Call
            </a>
          )}
          <a className="ghost-button" href={directionsUrl(provider)} target="_blank" rel="noopener noreferrer">
            <DirectionsIcon width={14} height={14} />
            Directions
          </a>
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
