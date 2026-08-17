import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, fetchProviderDetail } from '../api/client'
import type { ProviderDetailDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { AlertIcon, ChevronLeftIcon, DirectionsIcon, InfoIcon, PhoneIcon } from '../components/icons'
import { directionsUrl, formatDistance, formattedAddress, providerDisplayName, telHref } from '../utils/providerDisplay'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'

function parseIds(raw: string | null): number[] {
  return (raw ?? '')
    .split(',')
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isFinite(value) && value > 0)
}

function ComparePage() {
  const [searchParams] = useSearchParams()
  const ids = useMemo(() => parseIds(searchParams.get('ids')), [searchParams])

  const [providers, setProviders] = useState<ProviderDetailDto[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (ids.length === 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- resets state when no ids selected
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
      <main className="container compare-content">
        <Link to="/" className="back-link">
          <ChevronLeftIcon width={16} height={16} />
          Back to search
        </Link>

        <h1>Compare providers</h1>
        <p className="compare-intro">Factual navigation details only — not a clinical or quality recommendation.</p>

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
            <h3>No providers selected</h3>
            <p>Select 2 or 3 providers from your search results to compare them here.</p>
          </div>
        )}

        {status === 'success' && providers.length > 0 && (
          <div className="compare-table-wrapper">
            <table className="compare-table">
              <thead>
                <tr>
                  <th scope="col">Field</th>
                  {providers.map((provider) => (
                    <th scope="col" key={provider.id}>
                      {providerDisplayName(provider)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  <th scope="row">Specialty</th>
                  {providers.map((provider) => (
                    <td key={provider.id}>
                      {(provider.taxonomies.find((taxonomy) => taxonomy.primaryTaxonomy) ?? provider.taxonomies[0])
                        ?.displayName ?? '—'}
                    </td>
                  ))}
                </tr>
                <tr>
                  <th scope="row">Distance</th>
                  {providers.map((provider) => (
                    <td key={provider.id}>{provider.distanceMiles != null ? formatDistance(provider.distanceMiles) : '—'}</td>
                  ))}
                </tr>
                <tr>
                  <th scope="row">Practice location shown</th>
                  {providers.map((provider) => {
                    if (!provider.location) {
                      return <td key={provider.id}>—</td>
                    }
                    const { line1, line2 } = formattedAddress(provider.location)
                    return (
                      <td key={provider.id}>
                        {line1}
                        <br />
                        {line2}
                        {provider.otherLocations.length > 0 && (
                          <div className="field-hint">+{provider.otherLocations.length} other location(s)</div>
                        )}
                      </td>
                    )
                  })}
                </tr>
                <tr>
                  <th scope="row">Phone</th>
                  {providers.map((provider) => (
                    <td key={provider.id}>{provider.location?.phone ?? '—'}</td>
                  ))}
                </tr>
                <tr>
                  <th scope="row">NPI</th>
                  {providers.map((provider) => (
                    <td key={provider.id}>{provider.npiNumber}</td>
                  ))}
                </tr>
                <tr>
                  <th scope="row">Taxonomy</th>
                  {providers.map((provider) => (
                    <td key={provider.id}>{provider.taxonomies.map((taxonomy) => taxonomy.displayName).join(', ')}</td>
                  ))}
                </tr>
                <tr>
                  <th scope="row">Actions</th>
                  {providers.map((provider) => (
                    <td key={provider.id}>
                      <div className="compare-actions">
                        {provider.location?.phone && (
                          <a className="ghost-button" href={telHref(provider.location.phone)}>
                            <PhoneIcon width={14} height={14} />
                            Call
                          </a>
                        )}
                        {provider.location && (
                          <a
                            className="ghost-button"
                            href={directionsUrl(provider.location)}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <DirectionsIcon width={14} height={14} />
                            Directions
                          </a>
                        )}
                        <Link className="ghost-button" to={`/providers/${provider.id}`}>
                          View provider
                        </Link>
                      </div>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
        )}

        <div className="disclaimer-panel">
          <InfoIcon width={18} height={18} />
          <p>
            Comparison shows factual navigation data only — specialty, distance, and contact
            details. It is not a quality, ratings, or clinical recommendation.
          </p>
        </div>
      </main>
      <Footer />
    </div>
  )
}

export default ComparePage
