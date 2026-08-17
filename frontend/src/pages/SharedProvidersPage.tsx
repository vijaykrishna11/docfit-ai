import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, fetchProviderDetail } from '../api/client'
import type { ProviderDetailDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { AlertIcon, BadgeIcon, DirectionsIcon, LocationIcon, PhoneIcon } from '../components/icons'
import { directionsUrl, formattedAddress, initialsFor, providerDisplayName, telHref } from '../utils/providerDisplay'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'
export const MAX_SHARED_PROVIDERS = 5

function parseIds(raw: string | null): number[] {
  return (raw ?? '')
    .split(',')
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isFinite(value) && value > 0)
    .slice(0, MAX_SHARED_PROVIDERS)
}

/**
 * Public, unauthenticated view (CLAUDE.md "Shareable Shortlist -- Privacy Safe"). Renders only
 * public provider records from the ids in the URL -- never a user's account, shortlist name,
 * saved state, email, or any other private metadata. No backend sharing table: the link itself
 * (a plain list of provider ids) is the entire "share".
 */
function SharedProvidersPage() {
  const [searchParams] = useSearchParams()
  const ids = useMemo(() => parseIds(searchParams.get('ids')), [searchParams])

  const [providers, setProviders] = useState<ProviderDetailDto[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (ids.length === 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- resets state when no ids are present
      setProviders([])
      setStatus('success')
      return
    }
    let cancelled = false
    setStatus('loading')
    Promise.all(ids.map((id) => fetchProviderDetail(id)))
      .then((results) => {
        if (cancelled) return
        setProviders(results)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setStatus('error')
        setErrorMessage(error instanceof ApiError ? error.message : UNREACHABLE_MESSAGE)
      })
    return () => {
      cancelled = true
    }
  }, [ids])

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <div>
          <h1>Shared providers</h1>
          <p className="results-subtext">
            A list of providers shared with you from DocFit AI. Public directory data only -- not a
            clinical or quality recommendation.
          </p>
        </div>

        {status === 'loading' && <p className="results-heading-loading">Loading providers…</p>}

        {status === 'error' && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={22} height={22} />
            <div className="state-panel-copy">
              <h3>We couldn&rsquo;t load these providers</h3>
              <p>{errorMessage}</p>
            </div>
          </div>
        )}

        {status === 'success' && providers.length === 0 && (
          <div className="state-panel empty-panel">
            <h3>No providers to show</h3>
            <p>This share link doesn&rsquo;t include any valid providers.</p>
          </div>
        )}

        {status === 'success' && providers.length > 0 && (
          <ul className="provider-list">
            {providers.map((provider) => {
              const name = providerDisplayName(provider)
              const location = provider.location
              return (
                <li key={provider.id} className="provider-card">
                  <div className="provider-card-top">
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
                      <Link className="ghost-button" to={`/providers/${provider.id}`}>
                        View details
                      </Link>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </main>
      <Footer />
    </div>
  )
}

export default SharedProvidersPage
