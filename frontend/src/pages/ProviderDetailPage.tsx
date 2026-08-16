import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, fetchProviderDetail } from '../api/client'
import type { ProviderDetailDto, ProviderLocationDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import {
  AlertIcon,
  BadgeIcon,
  CheckIcon,
  ChevronLeftIcon,
  DirectionsIcon,
  InfoIcon,
  LocationIcon,
  PhoneIcon,
  ShareIcon,
} from '../components/icons'
import NetworkEvidenceDrawer from '../components/NetworkEvidenceDrawer'
import SaveProviderButton from '../components/SaveProviderButton'
import WhyThisResult from '../components/WhyThisResult'
import {
  directionsUrl,
  formatDistance,
  formattedAddress,
  initialsFor,
  providerDisplayName,
  telHref,
} from '../utils/providerDisplay'
import { recordRecentlyViewed } from '../utils/recentlyViewed'

type DetailStatus = 'loading' | 'success' | 'error' | 'not-found'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'

function ProviderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const [detail, setDetail] = useState<ProviderDetailDto | null>(null)
  const [status, setStatus] = useState<DetailStatus>('loading')
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect -- standard fetch-on-param-change pattern.
    setStatus('loading')

    const location = searchParams.get('location')
    const lat = searchParams.get('lat')
    const lng = searchParams.get('lng')

    fetchProviderDetail(Number(id), {
      location: lat && lng ? undefined : (location ?? undefined),
      lat: lat ? Number(lat) : undefined,
      lng: lng ? Number(lng) : undefined,
    })
      .then((result) => {
        if (cancelled) return
        setDetail(result)
        setStatus('success')
        const primaryTaxonomy = result.taxonomies.find((t) => t.primaryTaxonomy) ?? result.taxonomies[0]
        recordRecentlyViewed({
          id: result.id,
          name: providerDisplayName(result),
          specialtyDisplayName: primaryTaxonomy?.displayName ?? null,
        })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 404) {
          setStatus('not-found')
        } else {
          setStatus('error')
          setErrorMessage(error instanceof ApiError ? error.message : UNREACHABLE_MESSAGE)
        }
      })

    return () => {
      cancelled = true
    }
  }, [id, searchParams])

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <button type="button" className="back-link" onClick={() => navigate(-1)}>
          <ChevronLeftIcon width={16} height={16} />
          Back to results
        </button>

        {status === 'loading' && <p className="results-heading-loading">Loading provider details…</p>}

        {status === 'not-found' && (
          <div className="state-panel empty-panel">
            <h3>Provider not found</h3>
            <p>We couldn&rsquo;t find a provider with that ID in our current demo dataset.</p>
          </div>
        )}

        {status === 'error' && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={22} height={22} />
            <div className="state-panel-copy">
              <h3>We couldn&rsquo;t load this provider</h3>
              <p>{errorMessage}</p>
            </div>
          </div>
        )}

        {status === 'success' && detail && (
          <ProviderDetailCard detail={detail} planId={searchParams.get('planId') ? Number(searchParams.get('planId')) : undefined} />
        )}
      </main>
      <Footer />
    </div>
  )
}

function LocationPrecisionNote({ location }: { location: ProviderLocationDto }) {
  if (location.coordinatePrecision !== 'ZIP_CENTROID' && location.coordinatePrecision !== 'CITY_CENTROID') {
    return null
  }
  return (
    <p className="field-hint">
      <InfoIcon width={13} height={13} /> Distance and map position are approximate (ZIP-level), not this office's exact address.
    </p>
  )
}

function ProviderDetailCard({ detail, planId }: { detail: ProviderDetailDto; planId?: number }) {
  const name = providerDisplayName(detail)
  const location = detail.location
  const primaryTaxonomy = detail.taxonomies.find((taxonomy) => taxonomy.primaryTaxonomy) ?? detail.taxonomies[0]
  const [shareCopied, setShareCopied] = useState(false)
  const [evidenceDrawerOpen, setEvidenceDrawerOpen] = useState(false)

  async function handleShare() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setShareCopied(true)
      window.setTimeout(() => setShareCopied(false), 2500)
    } catch {
      // Clipboard access can be denied by the browser -- fail quietly rather than show a raw error.
    }
  }

  return (
    <article className="provider-detail-card">
      <div className="provider-detail-header">
        <div className="avatar avatar-lg" aria-hidden="true">
          {initialsFor(name)}
        </div>
        <div>
          <h1>{name}</h1>
          <div className="provider-detail-badges">
            {primaryTaxonomy && <span className="specialty-badge">{primaryTaxonomy.displayName}</span>}
            {detail.distanceMiles != null && (
              <span className="distance-badge">
                <LocationIcon width={13} height={13} />
                {formatDistance(detail.distanceMiles)} from your search
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="provider-detail-actions">
        {location?.phone && (
          <a className="primary-button" href={telHref(location.phone)}>
            <PhoneIcon width={16} height={16} />
            Call {location.phone}
          </a>
        )}
        {location && (
          <a className="secondary-button" href={directionsUrl(location)} target="_blank" rel="noopener noreferrer">
            <DirectionsIcon width={16} height={16} />
            Get directions
          </a>
        )}
        <SaveProviderButton providerId={detail.id} variant="labeled" />
        <button type="button" className={`secondary-button${shareCopied ? ' is-success' : ''}`} onClick={handleShare}>
          {shareCopied ? (
            <>
              <CheckIcon width={16} height={16} />
              Link copied
            </>
          ) : (
            <>
              <ShareIcon width={16} height={16} />
              Share
            </>
          )}
        </button>
      </div>

      <dl className="provider-detail-grid">
        {location && (
          <div>
            <dt>
              <LocationIcon width={13} height={13} />
              {detail.otherLocations.length > 0 ? 'Nearest practice location' : 'Practice address'}
            </dt>
            <dd>
              {formattedAddress(location).line1}
              <br />
              {formattedAddress(location).line2}
              <LocationPrecisionNote location={location} />
            </dd>
          </div>
        )}
        <div>
          <dt>
            <BadgeIcon width={13} height={13} />
            NPI
          </dt>
          <dd>{detail.npiNumber}</dd>
        </div>
        {location?.phone && (
          <div>
            <dt>
              <PhoneIcon width={13} height={13} />
              Phone
            </dt>
            <dd>{location.phone}</dd>
          </div>
        )}
      </dl>

      {detail.otherLocations.length > 0 && (
        <section className="provider-other-locations">
          <h2>Other locations</h2>
          <ul className="provider-other-locations-list">
            {detail.otherLocations.map((otherLocation) => {
              const { line1, line2 } = formattedAddress(otherLocation)
              return (
                <li key={otherLocation.id} className="provider-other-location-item">
                  <p className="detail">
                    <LocationIcon width={15} height={15} />
                    <span>
                      {line1}
                      <br />
                      {line2}
                    </span>
                  </p>
                  {otherLocation.phone && (
                    <p className="detail">
                      <PhoneIcon width={15} height={15} />
                      <span>{otherLocation.phone}</span>
                    </p>
                  )}
                  <LocationPrecisionNote location={otherLocation} />
                  <div className="provider-card-buttons">
                    {otherLocation.phone && (
                      <a className="ghost-button" href={telHref(otherLocation.phone)}>
                        <PhoneIcon width={14} height={14} />
                        Call
                      </a>
                    )}
                    <a className="ghost-button" href={directionsUrl(otherLocation)} target="_blank" rel="noopener noreferrer">
                      <DirectionsIcon width={14} height={14} />
                      Directions
                    </a>
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      <section className="provider-taxonomies">
        <h2>Taxonomy information</h2>
        <ul>
          {detail.taxonomies.map((taxonomy) => (
            <li key={taxonomy.taxonomyCode}>
              <span className="specialty-badge">{taxonomy.displayName}</span>
              {taxonomy.primaryTaxonomy && <span className="chip">Primary</span>}
              <span className="taxonomy-code">{taxonomy.taxonomyCode}</span>
            </li>
          ))}
        </ul>
      </section>

      {primaryTaxonomy && (
        <WhyThisResult
          specialtyDisplayName={primaryTaxonomy.displayName}
          npiNumber={detail.npiNumber}
          distanceMiles={detail.distanceMiles}
        />
      )}

      <div className="disclaimer-panel">
        <InfoIcon width={18} height={18} />
        <p>
          Provider information is sourced from public NPPES/NPI records and may change. Confirm
          details directly with the provider.
        </p>
      </div>

      {planId != null ? (
        <div className="provider-network-evidence-section">
          <button type="button" className="secondary-button" onClick={() => setEvidenceDrawerOpen(true)}>
            View network evidence
          </button>
          {evidenceDrawerOpen && (
            <NetworkEvidenceDrawer
              providerId={detail.id}
              planId={planId}
              locationId={location?.id}
              onClose={() => setEvidenceDrawerOpen(false)}
            />
          )}
        </div>
      ) : (
        <div className="disclaimer-panel">
          <InfoIcon width={18} height={18} />
          <p>Insurance coverage is not verified in this demo. Confirm directly with the provider or insurer.</p>
        </div>
      )}

      {detail.importedAt && (
        <p className="provenance-note">
          Imported into DocFit AI on{' '}
          {new Date(detail.importedAt).toLocaleDateString(undefined, {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
          })}{' '}
          from public NPPES/NPI records.
        </p>
      )}
    </article>
  )
}

export default ProviderDetailPage
